import { Inject, Injectable } from '@nestjs/common'
import { and, eq, inArray, isNull, lt, or, sql } from 'drizzle-orm'
import { DRIZZLE, type DrizzleDB } from '../database/database.module'
import { notifications, reminderEvents, reminders, users } from '../database/schema'
import type { NotificationRow } from '../notifications/notifications.repository'
import type { NotificationAnalysis } from './notification-analysis.schema'
import type { NotificationDigest } from './notification-analysis.schema'

@Injectable()
export class AnalysisRepository {
  constructor(@Inject(DRIZZLE) private readonly db: DrizzleDB) {}

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
    lastRunAt: Date | null
    processingAt: Date | null
  }>> {
    return this.db
      .select({
        userId: users.id,
        times: users.analysisTimes,
        timezone: users.analysisTimezone,
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
        title: reminders.title,
        description: reminders.description,
        status: reminders.status,
        quadrant: reminders.quadrant,
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
    const reminderValues = digest.reminders
      .filter((item) => allowedIds.has(item.sourceNotificationId))
      .map((item) => ({
        userId,
        sourceNotificationId: item.sourceNotificationId,
        title: item.title,
        description: item.description ?? null,
        reason: item.reason,
        importance: quadrantImportance(item.quadrant),
        quadrant: item.quadrant,
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
