import { Inject, Injectable } from '@nestjs/common'
import { and, eq } from 'drizzle-orm'
import { DRIZZLE, type DrizzleDB } from '../database/database.module'
import { notifications } from '../database/schema'

export interface NotificationRow {
  id: string
  userId: string
  deviceId: string
  notificationKey: string
  packageName: string
  title: string | null
  body: string | null
  subText: string | null
  timezone: string
  postedAt: Date
  receivedAt: Date
}

export interface UpsertResult {
  notification: NotificationRow
  created: boolean
}

@Injectable()
export class NotificationsRepository {
  constructor(@Inject(DRIZZLE) private readonly db: DrizzleDB) {}

  async upsert(params: {
    userId: string
    deviceId: string
    notificationKey: string
    packageName: string
    title?: string
    body?: string
    subText?: string
    timezone?: string
    postedAt: Date
  }): Promise<UpsertResult> {
    const values = {
      userId: params.userId,
      deviceId: params.deviceId,
      notificationKey: params.notificationKey,
      packageName: params.packageName,
      title: params.title ?? null,
      body: params.body ?? null,
      subText: params.subText ?? null,
      timezone: params.timezone ?? 'UTC',
      postedAt: params.postedAt,
    }

    const inserted = await this.db
      .insert(notifications)
      .values(values)
      .onConflictDoNothing({
        target: [notifications.deviceId, notifications.notificationKey],
      })
      .returning(this.selection())

    const row = inserted[0]
    if (row) {
      return { notification: row, created: true }
    }

    const existing = await this.db
      .select(this.selection())
      .from(notifications)
      .where(
        and(
          eq(notifications.deviceId, params.deviceId),
          eq(notifications.notificationKey, params.notificationKey),
        ),
      )
      .limit(1)

    const found = existing[0]
    if (!found) {
      throw new Error('failed to upsert notification')
    }
    return { notification: found, created: false }
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
