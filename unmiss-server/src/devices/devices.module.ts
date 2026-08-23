import { Module } from '@nestjs/common'
import { AuthModule } from '../auth/auth.module'
import { DevicesController } from './devices.controller'
import { DevicesRepository } from './devices.repository'
import { DevicesService } from './devices.service'

@Module({
  imports: [AuthModule],
  controllers: [DevicesController],
  providers: [DevicesRepository, DevicesService],
  exports: [DevicesService, DevicesRepository],
})
export class DevicesModule {}
