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
        now,
        300,
      )
      let runId: string | null = null
      try {
        runId = await this.analysisRepository.createRun(
          schedule.userId,
          notifications.length,
        )
        let digest: NotificationDigest = { reminders: [], updates: [] }
        if (notifications.length > 0) {
          const activeReminders = await this.analysisRepository.activeReminders(
            schedule.userId,
          )
          digest = await this.digestNotifications(
            notifications,
            activeReminders,
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
        await this.analysisRepository.completeRun(runId, digest)
        await this.analysisRepository.completeUserSchedule(schedule.userId, now)
        processedUsers += 1
      } catch (error) {
        const message = error instanceof Error ? error.message : 'unknown error'
        this.logger.warn(`scheduled digest failed for ${schedule.userId}: ${message}`)
        if (runId) await this.analysisRepository.failRun(runId, message)
        await this.analysisRepository.releaseMany(notifications.map((item) => item.id))
        await this.analysisRepository.releaseUserSchedule(schedule.userId)
      }
    }
    return { users: processedUsers, notifications: processedNotifications }
  }

  private async digestNotifications(
    notifications: NotificationRow[],
    activeReminders: Awaited<ReturnType<AnalysisRepository['activeReminders']>>,
    timezone: string,
    now: Date,
  ): Promise<NotificationDigest> {
    const digests: NotificationDigest[] = []
    for (let offset = 0; offset < notifications.length; offset += 50) {
      digests.push(
        await this.requestDigest({
          notifications: notifications.slice(offset, offset + 50).map(toPromptItem),
          existingReminders: activeReminders.map(toPromptReminder),
          timezone,
          currentTime: now.toISOString(),
        }),
      )
    }
    if (digests.length === 1) return digests[0]!
    return this.requestDigest({
      candidateReminders: digests.flatMap((digest) => digest.reminders),
      candidateUpdates: digests.flatMap((digest) => digest.updates),
      existingReminders: activeReminders.map(toPromptReminder),
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
      signal: AbortSignal.timeout(this.config.get<number>('AI_TIMEOUT_MS') ?? 180_000),
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

function toPromptReminder(reminder: Awaited<ReturnType<AnalysisRepository['activeReminders']>>[number]) {
  return {
    id: reminder.id,
    title: reminder.title,
    description: reminder.description,
    status: reminder.status,
    quadrant: reminder.quadrant,
    remindAt: reminder.remindAt.toISOString(),
  }
}

const SYSTEM_PROMPT = `You review a time segment of Android notifications to find
obligations the user may have overlooked while busy. Analyze the segment as a whole
and compare it with existingReminders. Use later notifications as evidence of changes,
completion, cancellation, supersession, or duplication. Prefer precision: exclude OTPs,
ads, news, likes, recommendations, FYI messages, and ordinary chat. Never invent an
obligation, completion, or deadline.

Classify every new or updated item into exactly one Eisenhower quadrant:
important_urgent, important_not_urgent, not_important_urgent, or
not_important_not_urgent.

Return JSON only as {"reminders":[],"updates":[]}. New reminders contain
sourceNotificationId, title, optional description, reason, quadrant, and absolute ISO
8601 remindAt. sourceNotificationId must be an input notification id. Updates contain
reminderId, action (update, complete, or ignore), reason, and optional changed fields.
Only complete or ignore an existing reminder when a later notification is explicit,
high-confidence evidence. Use update for changed time, content, or quadrant. Do not
repeat an existing reminder as new. Merge duplicates. remindAt must be after
currentTime; without an explicit deadline, use 09:00 local time on the next day.`
