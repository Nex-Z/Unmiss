import { Module } from '@nestjs/common'
import { AnalysisRepository } from './analysis.repository'
import { NotificationAnalysisService } from './notification-analysis.service'

@Module({
  providers: [AnalysisRepository, NotificationAnalysisService],
  exports: [NotificationAnalysisService],
})
export class AgentModule {}
