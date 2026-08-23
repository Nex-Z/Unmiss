import { ForbiddenException, Injectable } from '@nestjs/common'
import type { DevicePayload } from '../common/current-device.decorator'
import { NotificationAnalysisService } from '../agent/notification-analysis.service'
import {
  NotificationsRepository,
  type UpsertResult,
} from './notifications.repository'
import type { CreateNotificationDto } from './dto/create-notification.dto'

@Injectable()
export class NotificationsService {
  constructor(
    private readonly notificationsRepository: NotificationsRepository,
    private readonly notificationAnalysisService: NotificationAnalysisService,
  ) {}

  async create(
    device: DevicePayload,
    dto: CreateNotificationDto,
  ): Promise<UpsertResult> {
    if (dto.deviceId !== device.deviceId) {
      throw new ForbiddenException('deviceId does not match token')
    }
    const result = await this.notificationsRepository.upsert({
      userId: device.userId,
      deviceId: dto.deviceId,
      notificationKey: dto.notificationKey,
      packageName: dto.packageName,
      title: dto.title,
      body: dto.body,
      subText: dto.subText,
      timezone: this.validTimezone(dto.timezone),
      postedAt: new Date(dto.postedAt),
    })
    await this.notificationAnalysisService.analyzeById(result.notification.id)
    return result
  }

  private validTimezone(value?: string): string {
    if (!value) return 'UTC'
    try {
      new Intl.DateTimeFormat('en-US', { timeZone: value }).format()
      return value
    } catch {
      return 'UTC'
    }
  }
}
