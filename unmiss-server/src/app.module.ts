import { Module } from '@nestjs/common'
import { APP_GUARD } from '@nestjs/core'
import { AuthModule } from './auth/auth.module'
import { JwtAuthGuard } from './auth/jwt-auth.guard'
import { HealthController } from './common/controllers/health.controller'
import { configModule } from './config/config.module'
import { DatabaseModule } from './database/database.module'
import { DevicesModule } from './devices/devices.module'
import { NotificationsModule } from './notifications/notifications.module'
import { RemindersModule } from './reminders/reminders.module'
import { RateLimitGuard } from './common/rate-limit.guard'
import { WorkerSchedulerService } from './worker-scheduler.service'
import { AgentModule } from './agent/agent.module'

@Module({
  imports: [
    configModule,
    DatabaseModule,
    AgentModule,
    AuthModule,
    DevicesModule,
    NotificationsModule,
    RemindersModule,
  ],
  controllers: [HealthController],
  providers: [
    WorkerSchedulerService,
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: RateLimitGuard },
  ],
})
export class AppModule {}
