import { Inject, Injectable } from '@nestjs/common'
import { and, eq, isNull, lt, or, sql } from 'drizzle-orm'
import { DRIZZLE, type DrizzleDB } from '../database/database.module'
import { notifications, reminders } from '../database/schema'
import type { NotificationRow } from '../notifications/notifications.repository'
import type { NotificationAnalysis } from './notification-analysis.schema'

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
