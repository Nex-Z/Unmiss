import { Injectable, Logger } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import type { NotificationRow } from '../notifications/notifications.repository'
import { AnalysisRepository } from './analysis.repository'
import {
  notificationAnalysisSchema,
  type NotificationAnalysis,
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

  async analyzeById(id: string): Promise<void> {
    if (!this.isConfigured()) return
    const notification = await this.analysisRepository.claimById(id)
    if (!notification) return
    await this.analyzeClaimed(notification)
  }

  private async analyzeClaimed(notification: NotificationRow): Promise<boolean> {
    try {
      const analysis = await this.requestAnalysis(notification)
      await this.analysisRepository.save(notification, analysis)
      return true
    } catch (error) {
      const message = error instanceof Error ? error.message : 'unknown error'
      this.logger.warn(`notification analysis failed for ${notification.id}: ${message}`)
      await this.analysisRepository.release(notification.id)
      return false
    }
  }

  async processPending(limit = 20): Promise<number> {
    if (!this.isConfigured()) return 0
    const pending = await this.analysisRepository.claimUnprocessed(limit)
    let processed = 0
    for (const notification of pending) {
      if (await this.analyzeClaimed(notification)) processed += 1
    }
    return processed
  }

  private async requestAnalysis(
    notification: NotificationRow,
  ): Promise<NotificationAnalysis> {
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
            content: JSON.stringify({
              packageName: notification.packageName,
              title: notification.title,
              text: notification.body,
              subText: notification.subText,
              postedAt: notification.postedAt.toISOString(),
              timezone: notification.timezone,
              currentTime: new Date().toISOString(),
            }),
          },
        ],
      }),
      signal: AbortSignal.timeout(30_000),
    })
    if (!response.ok) throw new Error(`AI HTTP ${response.status}`)
    const payload = (await response.json()) as ChatCompletionResponse
    const content = payload.choices?.[0]?.message?.content
    if (!content) throw new Error('AI returned no content')
    const analysis = notificationAnalysisSchema.parse(JSON.parse(content))
    if (
      analysis.shouldCreateReminder &&
      new Date(analysis.remindAt) <= notification.postedAt
    ) {
      throw new Error('AI returned a reminder time that is not in the future')
    }
    return analysis
  }

  private apiKey(): string {
    return (
      this.config.get<string>('DEEPSEEK_API_KEY') ||
      this.config.get<string>('AI_API_KEY') ||
      ''
    ).trim()
  }
}

const SYSTEM_PROMPT = `You classify Android notifications into unfinished user obligations.
Prefer precision over recall. Ignore ads, news, likes, recommendations, FYI messages,
ordinary group chat, and system status. Create a reminder only when the user clearly
needs to take a future action. Resolve relative dates in the supplied IANA timezone,
using currentTime as the reference. Never invent a deadline. If an obligation has no
explicit time, schedule it for 20:00 local time on the same day (or next day when that
time has passed). All remindAt values must be absolute ISO 8601 timestamps with an
offset and must be after postedAt.

Return JSON only. If no reminder is needed:
{"shouldCreateReminder":false,"reason":"..."}

If a reminder is needed:
{"shouldCreateReminder":true,"title":"...","description":"...","reason":"...","importance":1,"remindAt":"..."}`
