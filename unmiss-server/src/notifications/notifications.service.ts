import { ForbiddenException, Injectable } from '@nestjs/common'
import type { DevicePayload } from '../common/current-device.decorator'
import {
  NotificationsRepository,
  type UpsertResult,
} from './notifications.repository'
import type { CreateNotificationDto } from './dto/create-notification.dto'
import type { CreateNotificationsBatchDto } from './dto/create-notifications-batch.dto'

@Injectable()
export class NotificationsService {
  constructor(
    private readonly notificationsRepository: NotificationsRepository,
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
    return result
  }

  async createBatch(
    device: DevicePayload,
    dto: CreateNotificationsBatchDto,
  ): Promise<{ accepted: number; created: number }> {
    if (dto.notifications.some((item) => item.deviceId !== device.deviceId)) {
      throw new ForbiddenException('deviceId does not match token')
    }
    const created = await this.notificationsRepository.insertMany(
      dto.notifications.map((item) => ({
        userId: device.userId,
        deviceId: item.deviceId,
        notificationKey: item.notificationKey,
        packageName: item.packageName,
        title: item.title,
        body: item.body,
        subText: item.subText,
        timezone: this.validTimezone(item.timezone),
        postedAt: new Date(item.postedAt),
      })),
    )
    return { accepted: dto.notifications.length, created }
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
