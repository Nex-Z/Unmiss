import {
  CanActivate,
  ExecutionContext,
  HttpException,
  HttpStatus,
  Injectable,
} from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import type { Request } from 'express'

interface RateBucket {
  count: number
  resetsAt: number
}

@Injectable()
export class RateLimitGuard implements CanActivate {
  private readonly buckets = new Map<string, RateBucket>()

  constructor(private readonly config: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>()
    const deviceId = (request.user as { deviceId?: string } | undefined)?.deviceId
    const key = deviceId ?? request.ip ?? 'unknown'
    const now = Date.now()
    const limit = this.config.get<number>('RATE_LIMIT_PER_MINUTE') ?? 120
    const bucket = this.buckets.get(key)
    if (!bucket || bucket.resetsAt <= now) {
      this.buckets.set(key, { count: 1, resetsAt: now + 60_000 })
      this.prune(now)
      return true
    }
    bucket.count += 1
    if (bucket.count > limit) {
      throw new HttpException('Too Many Requests', HttpStatus.TOO_MANY_REQUESTS)
    }
    return true
  }

  private prune(now: number): void {
    if (this.buckets.size < 10_000) return
    for (const [key, bucket] of this.buckets) {
      if (bucket.resetsAt <= now) this.buckets.delete(key)
    }
  }
}
