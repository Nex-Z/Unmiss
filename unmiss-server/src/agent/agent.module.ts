import { Module } from '@nestjs/common'
import { AnalysisRepository } from './analysis.repository'
import { NotificationAnalysisService } from './notification-analysis.service'
import { AnalysisController } from './analysis.controller'

@Module({
  controllers: [AnalysisController],
  providers: [AnalysisRepository, NotificationAnalysisService],
  exports: [NotificationAnalysisService],
})
export class AgentModule {}
