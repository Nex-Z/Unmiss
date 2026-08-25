import { Injectable, Logger } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import type { NotificationRow } from '../notifications/notifications.repository'
import { AnalysisRepository } from './analysis.repository'
import {
  notificationDigestSchema,
  type NotificationDigest,
} from './notification-analysis.schema'

interface ChatCompletionResponse {
  choices?: Array<{ message?: { content?: string } }>
}

@Injectable()
export class NotificationAnalysisService {
  private readonly logger = new Logger(NotificationAnalysisService.name)

  constructor(
    private readonly config: ConfigService,
    private readonly analysisRepository: AnalysisRepository,
  ) {}

  isConfigured(): boolean {
    return ['AI_MODEL', 'AI_BASE_URL'].every((key) =>
      Boolean(this.config.get<string>(key)?.trim()),
    ) && Boolean(this.apiKey())
  }

  async processDueSchedules(now = new Date()): Promise<{
    users: number
    notifications: number
  }> {
    if (!this.isConfigured()) return { users: 0, notifications: 0 }
    const schedules = await this.analysisRepository.analysisSchedules()
    let processedUsers = 0
    let processedNotifications = 0

    for (const schedule of schedules) {
      if (!isScheduleDue(schedule, now)) continue
      const claimed = await this.analysisRepository.claimUserSchedule(
        schedule.userId,
        new Date(now.getTime() - 10 * 60_000),
      )
      if (!claimed) continue

      const notifications = await this.analysisRepository.claimForUser(
        schedule.userId,
        new Date(now.getTime() - 30 * 60_000),
        300,
      )
      try {
        if (notifications.length > 0) {
          const digest = await this.digestNotifications(
            notifications,
            schedule.timezone,
            now,
          )
          await this.analysisRepository.saveDigest(
            schedule.userId,
            notifications.map((item) => item.id),
            digest,
          )
          processedNotifications += notifications.length
        }
        await this.analysisRepository.completeUserSchedule(schedule.userId, now)
        processedUsers += 1
      } catch (error) {
        const message = error instanceof Error ? error.message : 'unknown error'
        this.logger.warn(`scheduled digest failed for ${schedule.userId}: ${message}`)
        await this.analysisRepository.releaseMany(notifications.map((item) => item.id))
        await this.analysisRepository.releaseUserSchedule(schedule.userId)
      }
    }
    return { users: processedUsers, notifications: processedNotifications }
  }

  private async digestNotifications(
    notifications: NotificationRow[],
    timezone: string,
    now: Date,
  ): Promise<NotificationDigest> {
    const digests: NotificationDigest[] = []
    for (let offset = 0; offset < notifications.length; offset += 50) {
      digests.push(
        await this.requestDigest({
          notifications: notifications.slice(offset, offset + 50).map(toPromptItem),
          timezone,
          currentTime: now.toISOString(),
        }),
      )
    }
    if (digests.length === 1) return digests[0]!
    return this.requestDigest({
      candidateReminders: digests.flatMap((digest) => digest.reminders),
      timezone,
      currentTime: now.toISOString(),
      instruction: 'Deduplicate and consolidate these candidates across the full segment.',
    })
  }

  private async requestDigest(input: Record<string, unknown>): Promise<NotificationDigest> {
    const baseUrl = this.config.getOrThrow<string>('AI_BASE_URL').replace(/\/$/, '')
    const response = await fetch(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${this.apiKey()}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: this.config.getOrThrow<string>('AI_MODEL'),
        temperature: 0,
        response_format: { type: 'json_object' },
        messages: [
          { role: 'system', content: SYSTEM_PROMPT },
          {
            role: 'user',
            content: JSON.stringify(input),
          },
        ],
      }),
      signal: AbortSignal.timeout(30_000),
    })
    if (!response.ok) throw new Error(`AI HTTP ${response.status}`)
    const payload = (await response.json()) as ChatCompletionResponse
    const content = payload.choices?.[0]?.message?.content
    if (!content) throw new Error('AI returned no content')
    return notificationDigestSchema.parse(JSON.parse(content))
  }

  private apiKey(): string {
    return (
      this.config.get<string>('DEEPSEEK_API_KEY') ||
      this.config.get<string>('AI_API_KEY') ||
      ''
    ).trim()
  }
}

export function isScheduleDue(
  schedule: { times: string[]; timezone: string; lastRunAt: Date | null },
  now: Date,
): boolean {
  if (schedule.times.length === 0) return false
  const nowKey = localMinuteKey(now, schedule.timezone)
  const lastKey = schedule.lastRunAt
    ? localMinuteKey(schedule.lastRunAt, schedule.timezone)
    : ''
  const date = nowKey.slice(0, 10)
  const yesterday = localDateKey(new Date(now.getTime() - 24 * 60 * 60_000), schedule.timezone)
  const candidates = [date, yesterday]
    .flatMap((day) => schedule.times.map((time) => `${day}T${time}`))
    .filter((candidate) => candidate <= nowKey)
    .sort()
  const latest = candidates.at(-1)
  return Boolean(latest && latest > lastKey)
}

function localMinuteKey(date: Date, timezone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: timezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? ''
  return `${value('year')}-${value('month')}-${value('day')}T${value('hour')}:${value('minute')}`
}

function localDateKey(date: Date, timezone: string): string {
  return localMinuteKey(date, timezone).slice(0, 10)
}

function toPromptItem(notification: NotificationRow) {
  return {
    id: notification.id,
    packageName: notification.packageName,
    title: notification.title?.slice(0, 512) ?? null,
    text: notification.body?.slice(0, 1500) ?? null,
    subText: notification.subText?.slice(0, 256) ?? null,
    postedAt: notification.postedAt.toISOString(),
  }
}

const SYSTEM_PROMPT = `You review a time segment of Android notifications to find
secondary-important, secondary-urgent obligations the user may have overlooked while
busy. Analyze the segment as a whole. Use later notifications to infer completion,
supersession, or duplication. Prefer precision: exclude urgent alerts, OTPs, ads, news,
likes, recommendations, FYI messages, ordinary chat, and anything with evidence that
it was completed. Never invent an obligation or deadline.

Return JSON only as {"reminders":[]}. Each reminder must contain sourceNotificationId,
title, optional description, reason, importance (1-5), and an absolute ISO 8601
remindAt in the supplied timezone. sourceNotificationId must be an input id. Merge
duplicates into one reminder. remindAt must be after currentTime; when no explicit
deadline exists, use 09:00 local time on the next day.`
