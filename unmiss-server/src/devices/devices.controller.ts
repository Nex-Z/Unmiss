import { Body, Controller, Delete, Post } from '@nestjs/common'
import { CurrentDevice, type DevicePayload } from '../common/current-device.decorator'
import { Public } from '../common/public.decorator'
import type { RegisterDeviceResult } from './devices.service'
import { DevicesService } from './devices.service'
import { RegisterDeviceDto } from './dto/register-device.dto'
import { UpdatePushTokenDto } from './dto/update-push-token.dto'

@Controller('devices')
export class DevicesController {
  constructor(private readonly devicesService: DevicesService) {}

  @Public()
  @Post('register')
  async register(
    @Body() dto: RegisterDeviceDto,
  ): Promise<RegisterDeviceResult> {
    return this.devicesService.register(dto)
  }

  @Post('push-token')
  async updatePushToken(
    @CurrentDevice() device: DevicePayload,
    @Body() dto: UpdatePushTokenDto,
  ): Promise<{ updated: true }> {
    await this.devicesService.updatePushToken(device.deviceId, dto.pushToken)
    return { updated: true }
  }

  @Delete('me/data')
  async deleteMyData(
    @CurrentDevice() device: DevicePayload,
  ): Promise<{ deleted: true }> {
    await this.devicesService.deleteUserData(device.userId)
    return { deleted: true }
  }
}
