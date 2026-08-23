import { Module } from '@nestjs/common'
import { RemindersController } from './reminders.controller'
import { RemindersRepository } from './reminders.repository'
import { RemindersService } from './reminders.service'

@Module({
  controllers: [RemindersController],
  providers: [RemindersRepository, RemindersService],
  exports: [RemindersRepository, RemindersService],
})
export class RemindersModule {}
