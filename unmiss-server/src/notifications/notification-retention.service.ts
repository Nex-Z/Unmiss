import { Inject, Injectable } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { sql } from 'drizzle-orm'
import { DRIZZLE, type DrizzleDB } from '../database/database.module'

@Injectable()
export class NotificationRetentionService {
  constructor(
    @Inject(DRIZZLE) private readonly db: DrizzleDB,
    private readonly config: ConfigService,
  ) {}

  async purgeExpired(): Promise<number> {
    const days = this.config.get<number>('NOTIFICATION_RETENTION_DAYS') ?? 14
    return this.db.transaction(async (tx) => {
      await tx.execute(sql`
        UPDATE reminders
        SET source_notification_id = NULL, updated_at = now()
        WHERE source_notification_id IN (
          SELECT id FROM notifications
          WHERE received_at < now() - (${days} * interval '1 day')
        )
      `)
      const result = await tx.execute(sql`
        DELETE FROM notifications
        WHERE received_at < now() - (${days} * interval '1 day')
        RETURNING id
      `)
      return result.rows.length
    })
  }
}
