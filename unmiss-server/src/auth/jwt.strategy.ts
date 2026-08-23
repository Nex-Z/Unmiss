import { Inject, Injectable, UnauthorizedException } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { PassportStrategy } from '@nestjs/passport'
import { ExtractJwt, Strategy } from 'passport-jwt'
import type { DevicePayload } from '../common/current-device.decorator'
import { and, eq } from 'drizzle-orm'
import { DRIZZLE, type DrizzleDB } from '../database/database.module'
import { devices } from '../database/schema'

export interface JwtPayload extends DevicePayload {}

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(
    configService: ConfigService,
    @Inject(DRIZZLE) private readonly db: DrizzleDB,
  ) {
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: configService.getOrThrow<string>('JWT_SECRET'),
    })
  }

  async validate(payload: JwtPayload): Promise<DevicePayload> {
    const rows = await this.db
      .select({ id: devices.id })
      .from(devices)
      .where(
        and(eq(devices.id, payload.deviceId), eq(devices.userId, payload.userId)),
      )
      .limit(1)
    if (!rows[0]) throw new UnauthorizedException('unknown device')
    return { deviceId: payload.deviceId, userId: payload.userId }
  }
}
