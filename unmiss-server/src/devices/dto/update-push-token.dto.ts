import { IsNotEmpty, IsString, MaxLength } from 'class-validator'

export class UpdatePushTokenDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(4096)
  pushToken!: string
}
