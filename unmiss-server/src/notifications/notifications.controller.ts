import { Body, Controller, Post } from '@nestjs/common'
import {
  CurrentDevice,
  type DevicePayload,
} from '../common/current-device.decorator'
import { CreateNotificationDto } from './dto/create-notification.dto'
import { NotificationsService } from './notifications.service'

@Controller('notifications')
export class NotificationsController {
  constructor(private readonly notificationsService: NotificationsService) {}

  @Post()
  async create(
    @CurrentDevice() device: DevicePayload,
    @Body() dto: CreateNotificationDto,
  ): Promise<{ id: string; created: boolean }> {
    const result = await this.notificationsService.create(device, dto)
    return { id: result.notification.id, created: result.created }
  }
}
