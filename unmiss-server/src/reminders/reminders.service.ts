import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common'
import type { DevicePayload } from '../common/current-device.decorator'
import {
  RemindersRepository,
  type ReminderRow,
} from './reminders.repository'

@Injectable()
export class RemindersService {
  constructor(private readonly remindersRepository: RemindersRepository) {}

  pending(device: DevicePayload): Promise<ReminderRow[]> {
    return this.remindersRepository.pendingForUser(device.userId)
  }

  async done(device: DevicePayload, id: string): Promise<ReminderRow> {
    const reminder = await this.remindersRepository.setStatus({
      id,
      userId: device.userId,
      status: 'done',
    })
    if (!reminder) return this.existingTerminal(device.userId, id, 'done')
    return reminder
  }

  async ignore(device: DevicePayload, id: string): Promise<ReminderRow> {
    const reminder = await this.remindersRepository.setStatus({
      id,
      userId: device.userId,
      status: 'ignored',
    })
    if (!reminder) return this.existingTerminal(device.userId, id, 'ignored')
    return reminder
  }

  async snooze(
    device: DevicePayload,
    id: string,
    remindAt: Date,
  ): Promise<ReminderRow> {
    if (remindAt <= new Date()) {
      throw new BadRequestException('remindAt must be in the future')
    }
    const reminder = await this.remindersRepository.snooze({
      id,
      userId: device.userId,
      remindAt,
    })
    if (!reminder) {
      const existing = await this.remindersRepository.findForUser(id, device.userId)
      if (!existing) throw new NotFoundException('reminder not found')
      throw new ConflictException('only pending reminders can be snoozed')
    }
    return reminder
  }

  private async existingTerminal(
    userId: string,
    id: string,
    status: 'done' | 'ignored',
  ): Promise<ReminderRow> {
    const existing = await this.remindersRepository.findForUser(id, userId)
    if (!existing) throw new NotFoundException('reminder not found')
    if (existing.status === status) return existing
    throw new ConflictException('reminder is already in a terminal state')
  }
}
