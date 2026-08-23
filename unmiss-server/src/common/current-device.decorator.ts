import { createParamDecorator, ExecutionContext } from '@nestjs/common'

export interface DevicePayload {
  deviceId: string
  userId: string
}

export const CurrentDevice = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): DevicePayload => {
    const request = ctx.switchToHttp().getRequest()
    return request.user as DevicePayload
  },
)
