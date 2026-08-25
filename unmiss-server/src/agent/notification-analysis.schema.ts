import { z } from 'zod'

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

export const notificationDigestSchema = z.object({
  reminders: z.array(
    z.object({
      sourceNotificationId: z.string().uuid(),
      title: z.string().min(1).max(512),
      description: z.string().max(2000).optional(),
      reason: z.string().min(1).max(1000),
      importance: z.number().int().min(1).max(5),
      remindAt: z.string().datetime({ offset: true }),
    }),
  ).max(50),
})

export type NotificationDigest = z.infer<typeof notificationDigestSchema>
