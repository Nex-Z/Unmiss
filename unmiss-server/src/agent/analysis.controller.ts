import { Controller, Get } from '@nestjs/common'
import {
  CurrentDevice,
  type DevicePayload,
} from '../common/current-device.decorator'
import { AnalysisRepository } from './analysis.repository'

@Controller('analysis')
export class AnalysisController {
  constructor(private readonly repository: AnalysisRepository) {}

  @Get('runs')
  runs(@CurrentDevice() device: DevicePayload) {
    return this.repository.recentRuns(device.userId)
  }
}
