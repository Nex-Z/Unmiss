import { Inject, Injectable } from '@nestjs/common'
import { and, desc, eq, inArray, isNull, lt, or, sql } from 'drizzle-orm'
import { DRIZZLE, type DrizzleDB } from '../database/database.module'
import { analysisRuns, notifications, reminderEvents, reminders, users } from '../database/schema'
import type { NotificationRow } from '../notifications/notifications.repository'
import type { NotificationAnalysis } from './notification-analysis.schema'
import type { NotificationDigest } from './notification-analysis.schema'
import {
  DEFAULT_CATEGORY_WEIGHTS,
  type CategoryWeights,
} from '../common/reminder-categories'

export interface QualityStats {
  periodDays: number
  generatedAt: string
  analysis: {
    runs: number
    successfulRuns: number
    failedRuns: number
    notificationsAnalyzed: number
  }
  reminders: {
    created: number
    active: number
    completed: number
    ignored: number
    confirmed: number
    snoozed: number
    evaluated: number
    usefulRate: number | null
    ignoreRate: number | null
  }
  packages: Array<{
    packageName: string
    created: number
    completed: number
    ignored: number
  }>
  quadrants: Array<{ quadrant: string; count: number }>
}

@Injectable()
export class AnalysisRepository {
  constructor(@Inject(DRIZZLE) private readonly db: DrizzleDB) {}

  async createRun(userId: string, notificationCount: number): Promise<string> {
    const [run] = await this.db
      .insert(analysisRuns)
      .values({ userId, notificationCount })
      .returning({ id: analysisRuns.id })
    if (!run) throw new Error('failed to create analysis run')
    return run.id
  }

  async completeRun(runId: string, digest: NotificationDigest): Promise<void> {
    await this.db
      .update(analysisRuns)
      .set({
        status: 'success',
        reminderCount: digest.reminders.length,
        updateCount: digest.updates.length,
        result: digest,
        completedAt: new Date(),
      })
      .where(eq(analysisRuns.id, runId))
  }

  async failRun(runId: string, error: string): Promise<void> {
    await this.db
      .update(analysisRuns)
      .set({ status: 'failed', error: error.slice(0, 2000), completedAt: new Date() })
      .where(eq(analysisRuns.id, runId))
  }

  recentRuns(userId: string, limit = 50) {
    return this.db
      .select()
      .from(analysisRuns)
      .where(eq(analysisRuns.userId, userId))
      .orderBy(desc(analysisRuns.startedAt))
      .limit(limit)
  }

  async qualityStats(userId: string, periodDays = 14): Promise<QualityStats> {
    const [analysisResult, reminderResult, eventResult, packageResult, quadrantResult] =
      await Promise.all([
        this.db.execute(sql`
          SELECT
            count(*)::int AS "runs",
            count(*) FILTER (WHERE status = 'success')::int AS "successfulRuns",
            count(*) FILTER (WHERE status = 'failed')::int AS "failedRuns",
            coalesce(sum(notification_count) FILTER (WHERE status = 'success'), 0)::int
              AS "notificationsAnalyzed"
          FROM analysis_runs
          WHERE user_id = ${userId}
            AND started_at >= now() - (${periodDays} * interval '1 day')
        `),
        this.db.execute(sql`
          SELECT
            count(*)::int AS "created",
            count(*) FILTER (WHERE status IN ('candidate', 'pending'))::int AS "active",
            count(*) FILTER (WHERE status = 'done')::int AS "completed",
            count(*) FILTER (WHERE status = 'ignored')::int AS "ignored"
          FROM reminders
          WHERE user_id = ${userId}
            AND created_at >= now() - (${periodDays} * interval '1 day')
        `),
        this.db.execute(sql`
          SELECT
            count(*) FILTER (WHERE event.type = 'confirmed')::int AS "confirmed",
            count(*) FILTER (WHERE event.type = 'snoozed')::int AS "snoozed",
            count(DISTINCT CASE WHEN event.type IN ('confirmed', 'done') THEN event.reminder_id END)::int
              AS "usefulReminders",
            count(DISTINCT CASE WHEN event.type = 'ignored' THEN event.reminder_id END)::int
              AS "dismissedReminders"
          FROM reminder_events AS event
          INNER JOIN reminders AS reminder ON reminder.id = event.reminder_id
          WHERE reminder.user_id = ${userId}
            AND event.created_at >= now() - (${periodDays} * interval '1 day')
        `),
        this.db.execute(sql`
          SELECT
            notification.package_name AS "packageName",
            count(*)::int AS "created",
            count(*) FILTER (WHERE reminder.status = 'done')::int AS "completed",
            count(*) FILTER (WHERE reminder.status = 'ignored')::int AS "ignored"
          FROM reminders AS reminder
          INNER JOIN notifications AS notification
            ON notification.id = reminder.source_notification_id
          WHERE reminder.user_id = ${userId}
            AND reminder.created_at >= now() - (${periodDays} * interval '1 day')
          GROUP BY notification.package_name
          ORDER BY count(*) DESC, notification.package_name ASC
          LIMIT 8
        `),
        this.db.execute(sql`
          SELECT quadrant, count(*)::int AS "count"
          FROM reminders
          WHERE user_id = ${userId}
            AND created_at >= now() - (${periodDays} * interval '1 day')
          GROUP BY quadrant
          ORDER BY count(*) DESC
        `),
      ])

    const analysis = numericRow(analysisResult.rows[0], [
      'runs', 'successfulRuns', 'failedRuns', 'notificationsAnalyzed',
    ])
    const reminder = numericRow(reminderResult.rows[0], [
      'created', 'active', 'completed', 'ignored',
    ])
    const events = numericRow(eventResult.rows[0], [
      'confirmed', 'snoozed', 'usefulReminders', 'dismissedReminders',
    ])
    const decided = events.usefulReminders + events.dismissedReminders

    return {
      periodDays,
      generatedAt: new Date().toISOString(),
      analysis,
      reminders: {
        ...reminder,
        confirmed: events.confirmed,
        snoozed: events.snoozed,
        evaluated: decided,
        usefulRate: decided === 0 ? null : events.usefulReminders / decided,
        ignoreRate: decided === 0 ? null : events.dismissedReminders / decided,
      },
      packages: packageResult.rows.map((row) => ({
        packageName: String(row.packageName),
        created: Number(row.created),
        completed: Number(row.completed),
        ignored: Number(row.ignored),
      })),
      quadrants: quadrantResult.rows.map((row) => ({
        quadrant: String(row.quadrant),
        count: Number(row.count),
      })),
    }
  }

  async claimById(id: string): Promise<NotificationRow | null> {
    const staleAt = new Date(Date.now() - 5 * 60_000)
    const rows = await this.db
      .update(notifications)
      .set({ agentProcessingAt: new Date() })
      .where(
        and(
          eq(notifications.id, id),
          isNull(notifications.agentProcessedAt),
          or(
            isNull(notifications.agentProcessingAt),
            lt(notifications.agentProcessingAt, staleAt),
          ),
        ),
      )
      .returning(this.selection())
    return rows[0] ? normalizeNotificationRow(rows[0]) : null
  }

  async claimUnprocessed(limit: number): Promise<NotificationRow[]> {
    const result = await this.db.execute(sql`
      WITH claimed AS (
        SELECT id
        FROM notifications
        WHERE agent_processed_at IS NULL
          AND (agent_processing_at IS NULL OR agent_processing_at < now() - interval '5 minutes')
        ORDER BY received_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT ${limit}
      )
      UPDATE notifications AS notification
      SET agent_processing_at = now()
      FROM claimed
      WHERE notification.id = claimed.id
      RETURNING
        notification.id,
        notification.user_id AS "userId",
        notification.device_id AS "deviceId",
        notification.notification_key AS "notificationKey",
        notification.package_name AS "packageName",
        notification.title,
        notification.body,
        notification.sub_text AS "subText",
        notification.timezone,
        notification.posted_at AS "postedAt",
        notification.received_at AS "receivedAt"
    `)
    return result.rows.map(normalizeNotificationRow)
  }

  async analysisSchedules(): Promise<Array<{
    userId: string
    times: string[]
    timezone: string
    categoryWeights: CategoryWeights
    lastRunAt: Date | null
    processingAt: Date | null
  }>> {
    return this.db
      .select({
        userId: users.id,
        times: users.analysisTimes,
        timezone: users.analysisTimezone,
        categoryWeights: users.categoryWeights,
        lastRunAt: users.analysisLastRunAt,
        processingAt: users.analysisProcessingAt,
      })
      .from(users)
  }

  async claimUserSchedule(userId: string, staleAt: Date): Promise<boolean> {
    const rows = await this.db
      .update(users)
      .set({ analysisProcessingAt: new Date() })
      .where(
        and(
          eq(users.id, userId),
          or(
            isNull(users.analysisProcessingAt),
            lt(users.analysisProcessingAt, staleAt),
          ),
        ),
      )
      .returning({ id: users.id })
    return rows.length === 1
  }

  async claimForUser(userId: string, cutoff: Date, limit: number): Promise<NotificationRow[]> {
    const result = await this.db.execute(sql`
      WITH claimed AS (
        SELECT id
        FROM notifications
        WHERE user_id = ${userId}
          AND agent_processed_at IS NULL
          AND posted_at <= ${cutoff}
          AND (agent_processing_at IS NULL OR agent_processing_at < now() - interval '5 minutes')
        ORDER BY posted_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT ${limit}
      )
      UPDATE notifications AS notification
      SET agent_processing_at = now()
      FROM claimed
      WHERE notification.id = claimed.id
      RETURNING
        notification.id,
        notification.user_id AS "userId",
        notification.device_id AS "deviceId",
        notification.notification_key AS "notificationKey",
        notification.package_name AS "packageName",
        notification.title,
        notification.body,
        notification.sub_text AS "subText",
        notification.timezone,
        notification.posted_at AS "postedAt",
        notification.received_at AS "receivedAt"
    `)
    return result.rows.map(normalizeNotificationRow)
  }

  async completeUserSchedule(userId: string, completedAt: Date): Promise<void> {
    await this.db
      .update(users)
      .set({ analysisLastRunAt: completedAt, analysisProcessingAt: null })
      .where(eq(users.id, userId))
  }

  async releaseUserSchedule(userId: string): Promise<void> {
    await this.db
      .update(users)
      .set({ analysisProcessingAt: null })
      .where(eq(users.id, userId))
  }

  async activeReminders(userId: string) {
    return this.db
      .select({
        id: reminders.id,
        sourceNotificationId: reminders.sourceNotificationId,
        title: reminders.title,
        description: reminders.description,
        reason: reminders.reason,
        status: reminders.status,
        quadrant: reminders.quadrant,
        category: reminders.category,
        remindAt: reminders.remindAt,
      })
      .from(reminders)
      .where(
        and(
          eq(reminders.userId, userId),
          inArray(reminders.status, ['candidate', 'pending']),
        ),
      )
  }

  async release(id: string): Promise<void> {
    await this.db
      .update(notifications)
      .set({ agentProcessingAt: null })
      .where(and(eq(notifications.id, id), isNull(notifications.agentProcessedAt)))
  }

  async save(
    notification: { id: string; userId: string },
    analysis: NotificationAnalysis,
  ): Promise<void> {
    await this.db.transaction(async (tx) => {
      if (analysis.shouldCreateReminder) {
        await tx
          .insert(reminders)
          .values({
            userId: notification.userId,
            sourceNotificationId: notification.id,
            title: analysis.title,
            description: analysis.description ?? null,
            reason: analysis.reason,
            importance: analysis.importance,
            remindAt: new Date(analysis.remindAt),
          })
          .onConflictDoNothing({ target: reminders.sourceNotificationId })
      }
      await tx
        .update(notifications)
        .set({ agentProcessedAt: new Date(), agentProcessingAt: null })
        .where(eq(notifications.id, notification.id))
    })
  }

  async saveDigest(
    userId: string,
    notificationIds: string[],
    digest: NotificationDigest,
  ): Promise<void> {
    const allowedIds = new Set(notificationIds)
    const [preference] = await this.db
      .select({ weights: users.categoryWeights })
      .from(users)
      .where(eq(users.id, userId))
      .limit(1)
    const weights = preference?.weights ?? DEFAULT_CATEGORY_WEIGHTS
    const reminderValues = digest.reminders
      .filter((item) => allowedIds.has(item.sourceNotificationId) && weights[item.category] > 0)
      .map((item) => ({
        userId,
        sourceNotificationId: item.sourceNotificationId,
        title: item.title,
        description: item.description ?? null,
        reason: item.reason,
        importance: quadrantImportance(item.quadrant),
        quadrant: item.quadrant,
        category: item.category,
        status: 'candidate',
        remindAt: new Date(item.remindAt),
      }))

    await this.db.transaction(async (tx) => {
      if (reminderValues.length > 0) {
        await tx
          .insert(reminders)
          .values(reminderValues)
          .onConflictDoNothing({ target: reminders.sourceNotificationId })
      }
      for (const action of digest.updates) {
        const terminal = action.action === 'complete' || action.action === 'ignore'
        const [updated] = await tx
          .update(reminders)
          .set(
            terminal
              ? {
                  status: action.action === 'complete' ? 'done' : 'ignored',
                  completedAt: action.action === 'complete' ? new Date() : null,
                  reason: action.reason,
                  updatedAt: new Date(),
                }
              : {
                  ...(action.title ? { title: action.title } : {}),
                  ...(action.description !== undefined
                    ? { description: action.description }
                    : {}),
                  ...(action.quadrant
                    ? {
                        quadrant: action.quadrant,
                        importance: quadrantImportance(action.quadrant),
                      }
                    : {}),
                  ...(action.remindAt ? { remindAt: new Date(action.remindAt) } : {}),
                  reason: action.reason,
                  updatedAt: new Date(),
                },
          )
          .where(
            and(
              eq(reminders.id, action.reminderId),
              eq(reminders.userId, userId),
              inArray(reminders.status, ['candidate', 'pending']),
            ),
          )
          .returning({ id: reminders.id })
        if (updated) {
          await tx.insert(reminderEvents).values({
            reminderId: updated.id,
            type: `agent_${action.action}`,
            metadata: { reason: action.reason },
          })
        }
      }
      await tx
        .update(notifications)
        .set({ agentProcessedAt: new Date(), agentProcessingAt: null })
        .where(
          and(
            eq(notifications.userId, userId),
            inArray(notifications.id, notificationIds),
          ),
        )
    })
  }

  async releaseMany(notificationIds: string[]): Promise<void> {
    if (notificationIds.length === 0) return
    await this.db
      .update(notifications)
      .set({ agentProcessingAt: null })
      .where(inArray(notifications.id, notificationIds))
  }

  private selection() {
    return {
      id: notifications.id,
      userId: notifications.userId,
      deviceId: notifications.deviceId,
      notificationKey: notifications.notificationKey,
      packageName: notifications.packageName,
      title: notifications.title,
      body: notifications.body,
      subText: notifications.subText,
      timezone: notifications.timezone,
      postedAt: notifications.postedAt,
      receivedAt: notifications.receivedAt,
    }
  }
}

function quadrantImportance(quadrant: string): number {
  return {
    important_urgent: 4,
    important_not_urgent: 3,
    not_important_urgent: 2,
    not_important_not_urgent: 1,
  }[quadrant] ?? 1
}

export function normalizeNotificationRow(row: Record<string, unknown>): NotificationRow {
  return {
    id: String(row.id),
    userId: String(row.userId),
    deviceId: String(row.deviceId),
    notificationKey: String(row.notificationKey),
    packageName: String(row.packageName),
    title: nullableString(row.title),
    body: nullableString(row.body),
    subText: nullableString(row.subText),
    timezone: String(row.timezone),
    postedAt: requiredDate(row.postedAt, 'postedAt'),
    receivedAt: requiredDate(row.receivedAt, 'receivedAt'),
  }
}

function nullableString(value: unknown): string | null {
  return value === null || value === undefined ? null : String(value)
}

function requiredDate(value: unknown, field: string): Date {
  const date = value instanceof Date ? value : new Date(String(value))
  if (Number.isNaN(date.getTime())) throw new Error(`invalid ${field} from database`)
  return date
}

function numericRow<K extends string>(
  row: Record<string, unknown> | undefined,
  keys: K[],
): Record<K, number> {
  return Object.fromEntries(keys.map((key) => [key, Number(row?.[key] ?? 0)])) as Record<K, number>
}
