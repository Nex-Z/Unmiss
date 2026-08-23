import {
  IsDateString,
  IsNotEmpty,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
} from 'class-validator'

export class CreateNotificationDto {
  @IsUUID()
  deviceId!: string

  @IsString()
  @IsNotEmpty()
  @MaxLength(512)
  notificationKey!: string

  @IsString()
  @IsNotEmpty()
  @MaxLength(256)
  packageName!: string

  @IsOptional()
  @IsString()
  @MaxLength(1024)
  title?: string

  @IsOptional()
  @IsString()
  @MaxLength(4096)
  body?: string

  @IsOptional()
  @IsString()
  @MaxLength(512)
  subText?: string

  @IsDateString()
  postedAt!: string

  @IsOptional()
  @IsString()
  @MaxLength(128)
  timezone?: string
}
