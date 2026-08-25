import { Injectable, Logger, OnApplicationShutdown } from '@nestjs/common'
import { NotificationAnalysisService } from './agent/notification-analysis.service'
import { NotificationRetentionService } from './notifications/notification-retention.service'

@Injectable()
export class WorkerSchedulerService implements OnApplicationShutdown {
  private readonly logger = new Logger('Worker')
  private analysisTimer?: NodeJS.Timeout
  private retentionTimer?: NodeJS.Timeout
  private running = false

  constructor(
    private readonly analysisService: NotificationAnalysisService,
    private readonly retentionService: NotificationRetentionService,
  ) {}

  start(): void {
    if (this.running) return
    this.running = true
    this.logger.log('Unmiss worker started, database connected')
    void this.processNotifications()
    void this.purgeNotifications()
  }

  stop(): void {
    this.running = false
    if (this.analysisTimer) clearTimeout(this.analysisTimer)
    if (this.retentionTimer) clearTimeout(this.retentionTimer)
    this.analysisTimer = undefined
    this.retentionTimer = undefined
  }

  onApplicationShutdown(): void {
    this.stop()
  }

  private async processNotifications(): Promise<void> {
    try {
      const result = await this.analysisService.processDueSchedules()
      if (result.users > 0) {
        this.logger.log(
          `digested ${result.notifications} notifications for ${result.users} user(s)`,
        )
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'unknown error'
      this.logger.error(`notification processing failed: ${message}`)
    } finally {
      if (this.running) {
        this.analysisTimer = setTimeout(() => void this.processNotifications(), 30_000)
      }
    }
  }

  private async purgeNotifications(): Promise<void> {
    try {
      const count = await this.retentionService.purgeExpired()
      if (count > 0) this.logger.log(`purged ${count} expired notifications`)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'unknown error'
      this.logger.error(`notification retention failed: ${message}`)
    } finally {
      if (this.running) {
        this.retentionTimer = setTimeout(
          () => void this.purgeNotifications(),
          6 * 60 * 60 * 1000,
        )
      }
    }
  }
}
