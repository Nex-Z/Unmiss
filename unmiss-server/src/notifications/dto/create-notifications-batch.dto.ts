import { Type } from 'class-transformer'
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  ValidateNested,
} from 'class-validator'
import { CreateNotificationDto } from './create-notification.dto'

export class CreateNotificationsBatchDto {
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(100)
  @ValidateNested({ each: true })
  @Type(() => CreateNotificationDto)
  notifications!: CreateNotificationDto[]
}
