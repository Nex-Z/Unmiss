import { relations, sql } from 'drizzle-orm'
import {
  index,
  jsonb,
  pgTable,
  smallint,
  text,
  timestamp,
  uniqueIndex,
  uuid,
} from 'drizzle-orm/pg-core'

export const users = pgTable('users', {
  id: uuid('id').primaryKey().defaultRandom(),
  createdAt: timestamp('created_at', { withTimezone: true })
    .notNull()
    .defaultNow(),
})

export const devices = pgTable(
  'devices',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    name: text('name'),
    platform: text('platform').notNull(),
    pushToken: text('push_token'),
    createdAt: timestamp('created_at', { withTimezone: true })
      .notNull()
      .defaultNow(),
    lastSeenAt: timestamp('last_seen_at', { withTimezone: true }),
  },
  (t) => [index('devices_user_id_idx').on(t.userId)],
)

export const notifications = pgTable(
  'notifications',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    deviceId: uuid('device_id')
      .notNull()
      .references(() => devices.id),
    notificationKey: text('notification_key').notNull(),
    packageName: text('package_name').notNull(),
    title: text('title'),
    body: text('body'),
    subText: text('sub_text'),
    timezone: text('timezone').notNull().default('UTC'),
    postedAt: timestamp('posted_at', { withTimezone: true }).notNull(),
    receivedAt: timestamp('received_at', { withTimezone: true })
      .notNull()
      .defaultNow(),
    agentProcessedAt: timestamp('agent_processed_at', { withTimezone: true }),
    agentProcessingAt: timestamp('agent_processing_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true })
      .notNull()
      .defaultNow(),
  },
  (t) => [
    uniqueIndex('notifications_device_key_uq').on(
      t.deviceId,
      t.notificationKey,
    ),
    index('notifications_user_posted_idx').on(t.userId, t.postedAt),
  ],
)

export const reminders = pgTable(
  'reminders',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    sourceNotificationId: uuid('source_notification_id').references(
      () => notifications.id,
    ),
    title: text('title').notNull(),
    description: text('description'),
    reason: text('reason'),
    importance: smallint('importance'),
    status: text('status').notNull().default('pending'),
    remindAt: timestamp('remind_at', { withTimezone: true }).notNull(),
    lastShownAt: timestamp('last_shown_at', { withTimezone: true }),
    completedAt: timestamp('completed_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true })
      .notNull()
      .defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true })
      .notNull()
      .default(sql`now()`),
  },
  (t) => [
    uniqueIndex('reminders_source_notification_uq').on(t.sourceNotificationId),
    index('reminders_due_idx')
      .on(t.status, t.remindAt)
      .where(sql`status = 'pending'`),
  ],
)

export const reminderEvents = pgTable(
  'reminder_events',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    reminderId: uuid('reminder_id')
      .notNull()
      .references(() => reminders.id),
    type: text('type').notNull(),
    metadata: jsonb('metadata'),
    createdAt: timestamp('created_at', { withTimezone: true })
      .notNull()
      .defaultNow(),
  },
  (t) => [index('reminder_events_reminder_idx').on(t.reminderId)],
)

export const usersRelations = relations(users, ({ many }) => ({
  devices: many(devices),
  notifications: many(notifications),
  reminders: many(reminders),
}))

export const devicesRelations = relations(devices, ({ one, many }) => ({
  user: one(users, { fields: [devices.userId], references: [users.id] }),
  notifications: many(notifications),
}))

export const notificationsRelations = relations(notifications, ({ one }) => ({
  user: one(users, { fields: [notifications.userId], references: [users.id] }),
  device: one(devices, {
    fields: [notifications.deviceId],
    references: [devices.id],
  }),
}))

export const remindersRelations = relations(reminders, ({ one, many }) => ({
  user: one(users, { fields: [reminders.userId], references: [users.id] }),
  sourceNotification: one(notifications, {
    fields: [reminders.sourceNotificationId],
    references: [notifications.id],
  }),
  events: many(reminderEvents),
}))

export const reminderEventsRelations = relations(reminderEvents, ({ one }) => ({
  reminder: one(reminders, {
    fields: [reminderEvents.reminderId],
    references: [reminders.id],
  }),
}))
