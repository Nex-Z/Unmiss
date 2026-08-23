import { Inject, Injectable } from '@nestjs/common'
import { and, asc, eq } from 'drizzle-orm'
import { DRIZZLE, type DrizzleDB } from '../database/database.module'
import { reminderEvents, reminders } from '../database/schema'

export type ReminderRow = typeof reminders.$inferSelect

@Injectable()
export class RemindersRepository {
  constructor(@Inject(DRIZZLE) private readonly db: DrizzleDB) {}

  async pendingForUser(userId: string): Promise<ReminderRow[]> {
    return this.db
      .select()
      .from(reminders)
      .where(and(eq(reminders.userId, userId), eq(reminders.status, 'pending')))
      .orderBy(asc(reminders.remindAt))
  }

  async findForUser(id: string, userId: string): Promise<ReminderRow | null> {
    const rows = await this.db
      .select()
      .from(reminders)
      .where(and(eq(reminders.id, id), eq(reminders.userId, userId)))
      .limit(1)
    return rows[0] ?? null
  }

  async setStatus(params: {
    id: string
    userId: string
    status: 'done' | 'ignored'
  }): Promise<ReminderRow | null> {
    return this.db.transaction(async (tx) => {
      const updated = await tx
        .update(reminders)
        .set({
          status: params.status,
          completedAt: params.status === 'done' ? new Date() : null,
          updatedAt: new Date(),
        })
        .where(
          and(
            eq(reminders.id, params.id),
            eq(reminders.userId, params.userId),
            eq(reminders.status, 'pending'),
          ),
        )
        .returning()
      const reminder = updated[0]
      if (!reminder) return null
      await tx.insert(reminderEvents).values({
        reminderId: reminder.id,
        type: params.status,
      })
      return reminder
    })
  }

  async snooze(params: {
    id: string
    userId: string
    remindAt: Date
  }): Promise<ReminderRow | null> {
    return this.db.transaction(async (tx) => {
      const updated = await tx
        .update(reminders)
        .set({
          status: 'pending',
          remindAt: params.remindAt,
          completedAt: null,
          lastShownAt: null,
          updatedAt: new Date(),
        })
        .where(
          and(
            eq(reminders.id, params.id),
            eq(reminders.userId, params.userId),
            eq(reminders.status, 'pending'),
          ),
        )
        .returning()
      const reminder = updated[0]
      if (!reminder) return null
      await tx.insert(reminderEvents).values({
        reminderId: reminder.id,
        type: 'snoozed',
        metadata: { remindAt: params.remindAt.toISOString() },
      })
      return reminder
    })
  }
}
