import { Module } from '@nestjs/common'
import { AuthModule } from '../auth/auth.module'
import { AgentModule } from '../agent/agent.module'
import { NotificationsController } from './notifications.controller'
import { NotificationsRepository } from './notifications.repository'
import { NotificationsService } from './notifications.service'
import { NotificationRetentionService } from './notification-retention.service'

@Module({
  imports: [AuthModule, AgentModule],
  controllers: [NotificationsController],
  providers: [NotificationsRepository, NotificationsService, NotificationRetentionService],
  exports: [NotificationRetentionService],
})
export class NotificationsModule {}
