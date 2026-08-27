import { z } from 'zod'
import { REMINDER_CATEGORIES } from '../common/reminder-categories'

export const notificationAnalysisSchema = z.discriminatedUnion(
  'shouldCreateReminder',
  [
    z.object({
      shouldCreateReminder: z.literal(false),
      reason: z.string().min(1).max(1000),
    }),
    z.object({
      shouldCreateReminder: z.literal(true),
      title: z.string().min(1).max(512),
      description: z.string().max(2000).optional(),
      reason: z.string().min(1).max(1000),
      importance: z.number().int().min(1).max(5),
      remindAt: z.string().datetime({ offset: true }),
    }),
  ],
)

export type NotificationAnalysis = z.infer<typeof notificationAnalysisSchema>

export const reminderQuadrantSchema = z.enum([
  'important_urgent',
  'important_not_urgent',
  'not_important_urgent',
  'not_important_not_urgent',
])

export const reminderCategorySchema = z.enum(REMINDER_CATEGORIES)

const digestReminderSchema = z.object({
  sourceNotificationId: z.string().uuid(),
  title: z.string().min(1).max(512),
  description: z.string().max(2000).optional(),
  reason: z.string().min(1).max(1000),
  category: reminderCategorySchema,
  quadrant: reminderQuadrantSchema,
  remindAt: z.string().datetime({ offset: true }),
})

export const notificationDigestSchema = z.object({
  reminders: z.array(digestReminderSchema).max(50),
  updates: z.array(
    z.object({
      reminderId: z.string().uuid(),
      action: z.enum(['update', 'complete', 'ignore']),
      title: z.string().min(1).max(512).optional(),
      description: z.string().max(2000).optional(),
      reason: z.string().min(1).max(1000),
      quadrant: reminderQuadrantSchema.optional(),
      remindAt: z.string().datetime({ offset: true }).optional(),
    }),
  ).max(50).default([]),
})

export type NotificationDigest = z.infer<typeof notificationDigestSchema>
