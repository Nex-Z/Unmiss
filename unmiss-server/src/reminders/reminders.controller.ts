import { Body, Controller, Get, Param, ParseUUIDPipe, Post } from '@nestjs/common'
import {
  CurrentDevice,
  type DevicePayload,
} from '../common/current-device.decorator'
import { SnoozeReminderDto } from './dto/snooze-reminder.dto'
import type { ReminderRow } from './reminders.repository'
import { RemindersService } from './reminders.service'

@Controller('reminders')
export class RemindersController {
  constructor(private readonly remindersService: RemindersService) {}

  @Get('pending')
  pending(@CurrentDevice() device: DevicePayload): Promise<ReminderRow[]> {
    return this.remindersService.pending(device)
  }

  @Get('inbox')
  inbox(@CurrentDevice() device: DevicePayload): Promise<ReminderRow[]> {
    return this.remindersService.inbox(device)
  }

  @Post(':id/done')
  done(
    @CurrentDevice() device: DevicePayload,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<ReminderRow> {
    return this.remindersService.done(device, id)
  }

  @Post(':id/ignore')
  ignore(
    @CurrentDevice() device: DevicePayload,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<ReminderRow> {
    return this.remindersService.ignore(device, id)
  }

  @Post(':id/snooze')
  snooze(
    @CurrentDevice() device: DevicePayload,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SnoozeReminderDto,
  ): Promise<ReminderRow> {
    return this.remindersService.snooze(device, id, new Date(dto.remindAt))
  }

  @Post(':id/confirm')
  confirm(
    @CurrentDevice() device: DevicePayload,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SnoozeReminderDto,
  ): Promise<ReminderRow> {
    return this.remindersService.confirm(device, id, new Date(dto.remindAt))
  }
}
