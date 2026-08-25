import { Body, Controller, Post } from '@nestjs/common'
import {
  CurrentDevice,
  type DevicePayload,
} from '../common/current-device.decorator'
import { CreateNotificationDto } from './dto/create-notification.dto'
import { CreateNotificationsBatchDto } from './dto/create-notifications-batch.dto'
import { NotificationsService } from './notifications.service'

@Controller('notifications')
export class NotificationsController {
  constructor(private readonly notificationsService: NotificationsService) {}

  @Post('batch')
  createBatch(
    @CurrentDevice() device: DevicePayload,
    @Body() dto: CreateNotificationsBatchDto,
  ): Promise<{ accepted: number; created: number }> {
    return this.notificationsService.createBatch(device, dto)
  }

  @Post()
  async create(
    @CurrentDevice() device: DevicePayload,
    @Body() dto: CreateNotificationDto,
  ): Promise<{ id: string; created: boolean }> {
    const result = await this.notificationsService.create(device, dto)
    return { id: result.notification.id, created: result.created }
  }
}
