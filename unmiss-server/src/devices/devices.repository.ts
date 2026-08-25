import { Inject, Injectable } from '@nestjs/common'
import { eq, sql } from 'drizzle-orm'
import { DRIZZLE, type DrizzleDB } from '../database/database.module'
import { devices, users } from '../database/schema'

export interface DeviceRow {
  id: string
  userId: string
  name: string | null
  platform: string
  createdAt: Date
  lastSeenAt: Date | null
}

export interface AnalysisScheduleRow {
  times: string[]
  timezone: string
  lastRunAt: Date | null
}

@Injectable()
export class DevicesRepository {
  constructor(@Inject(DRIZZLE) private readonly db: DrizzleDB) {}

  async createUser(): Promise<{ id: string }> {
    const [row] = await this.db.insert(users).values({}).returning({ id: users.id })
    if (!row) {
      throw new Error('failed to create user')
    }
    return row
  }

  async createDevice(params: {
    userId: string
    name?: string
    platform: string
  }): Promise<DeviceRow> {
    const values = {
      userId: params.userId,
      platform: params.platform,
      ...(params.name !== undefined ? { name: params.name } : {}),
    }
    const rows = await this.db.insert(devices).values(values).returning({
      id: devices.id,
      userId: devices.userId,
      name: devices.name,
      platform: devices.platform,
      createdAt: devices.createdAt,
      lastSeenAt: devices.lastSeenAt,
    })
    const row = rows[0]
    if (!row) {
      throw new Error('failed to create device')
    }
    return row
  }

  async findById(id: string): Promise<DeviceRow | null> {
    const rows = await this.db
      .select({
        id: devices.id,
        userId: devices.userId,
        name: devices.name,
        platform: devices.platform,
        createdAt: devices.createdAt,
        lastSeenAt: devices.lastSeenAt,
      })
      .from(devices)
      .where(eq(devices.id, id))
      .limit(1)
    return rows[0] ?? null
  }

  async touchLastSeen(id: string): Promise<void> {
    await this.db
      .update(devices)
      .set({ lastSeenAt: new Date() })
      .where(eq(devices.id, id))
  }

  async updatePushToken(id: string, pushToken: string): Promise<void> {
    await this.db
      .update(devices)
      .set({ pushToken, lastSeenAt: new Date() })
      .where(eq(devices.id, id))
  }

  async analysisSchedule(userId: string): Promise<AnalysisScheduleRow> {
    const [row] = await this.db
      .select({
        times: users.analysisTimes,
        timezone: users.analysisTimezone,
        lastRunAt: users.analysisLastRunAt,
      })
      .from(users)
      .where(eq(users.id, userId))
      .limit(1)
    if (!row) throw new Error('user not found')
    return row
  }

  async updateAnalysisSchedule(
    userId: string,
    times: string[],
    timezone: string,
  ): Promise<AnalysisScheduleRow> {
    const [row] = await this.db
      .update(users)
      .set({
        analysisTimes: [...new Set(times)].sort(),
        analysisTimezone: timezone,
        analysisProcessingAt: null,
      })
      .where(eq(users.id, userId))
      .returning({
        times: users.analysisTimes,
        timezone: users.analysisTimezone,
        lastRunAt: users.analysisLastRunAt,
      })
    if (!row) throw new Error('user not found')
    return row
  }

  async deleteUserData(userId: string): Promise<void> {
    await this.db.transaction(async (tx) => {
      await tx.execute(sql`
        DELETE FROM reminder_events AS event
        USING reminders AS reminder
        WHERE event.reminder_id = reminder.id AND reminder.user_id = ${userId}
      `)
      await tx.execute(sql`DELETE FROM reminders WHERE user_id = ${userId}`)
      await tx.execute(sql`DELETE FROM notifications WHERE user_id = ${userId}`)
      await tx.execute(sql`DELETE FROM devices WHERE user_id = ${userId}`)
      await tx.execute(sql`DELETE FROM users WHERE id = ${userId}`)
    })
  }
}
