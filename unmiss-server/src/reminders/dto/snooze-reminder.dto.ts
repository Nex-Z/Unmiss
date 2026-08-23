import { IsDateString } from 'class-validator'

export class SnoozeReminderDto {
  @IsDateString()
  remindAt!: string
}
