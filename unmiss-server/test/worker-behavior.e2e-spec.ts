import { INestApplication } from '@nestjs/common'
import { Test } from '@nestjs/testing'
import { eq, inArray } from 'drizzle-orm'
import { AppModule } from '../src/app.module'
import { DRIZZLE, type DrizzleDB } from '../src/database/database.module'
import {
  devices,
  notifications,
  reminderEvents,
  reminders,
  users,
} from '../src/database/schema'
import { NotificationRetentionService } from '../src/notifications/notification-retention.service'
import { AnalysisRepository } from '../src/agent/analysis.repository'

describe('Worker behavior (e2e)', () => {
  let app: INestApplication
  let db: DrizzleDB
  let retention: NotificationRetentionService
  let analysisRepository: AnalysisRepository
  const userIds: string[] = []
  const deviceIds: string[] = []
  const notificationIds: string[] = []
  const reminderIds: string[] = []

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile()
    app = moduleRef.createNestApplication()
    await app.init()
    db = moduleRef.get(DRIZZLE)
    retention = moduleRef.get(NotificationRetentionService)
    analysisRepository = moduleRef.get(AnalysisRepository)
  })

  afterAll(async () => {
    if (reminderIds.length) {
      await db.delete(reminderEvents).where(inArray(reminderEvents.reminderId, reminderIds))
      await db.delete(reminders).where(inArray(reminders.id, reminderIds))
    }
    if (notificationIds.length) {
      await db.delete(notifications).where(inArray(notifications.id, notificationIds))
    }
    if (deviceIds.length) await db.delete(devices).where(inArray(devices.id, deviceIds))
    if (userIds.length) await db.delete(users).where(inArray(users.id, userIds))
    await app.close()
  })

  async function createIdentity(): Promise<{ userId: string; deviceId: string }> {
    const [user] = await db.insert(users).values({}).returning({ id: users.id })
    const [device] = await db
      .insert(devices)
      .values({ userId: user!.id, platform: 'test' })
      .returning({ id: devices.id })
    userIds.push(user!.id)
    deviceIds.push(device!.id)
    return { userId: user!.id, deviceId: device!.id }
  }

  it('atomically claims a notification only once', async () => {
    const identity = await createIdentity()
    const [notification] = await db
      .insert(notifications)
      .values({
        ...identity,
        notificationKey: `claim-${Date.now()}`,
        packageName: 'test',
        body: '明天提交材料',
        timezone: 'Asia/Hong_Kong',
        postedAt: new Date(),
      })
      .returning({ id: notifications.id })
    notificationIds.push(notification!.id)

    const claims = await Promise.all([
      analysisRepository.claimById(notification!.id),
      analysisRepository.claimById(notification!.id),
    ])
    expect(claims.filter(Boolean)).toHaveLength(1)
  })

  it('purges expired notification text but retains its reminder', async () => {
    const identity = await createIdentity()
    const [notification] = await db
      .insert(notifications)
      .values({
        ...identity,
        notificationKey: `retention-${Date.now()}`,
        packageName: 'test',
        body: 'sensitive old text',
        postedAt: new Date(Date.now() - 20 * 86_400_000),
        receivedAt: new Date(Date.now() - 20 * 86_400_000),
      })
      .returning({ id: notifications.id })
    notificationIds.push(notification!.id)
    const [reminder] = await db
      .insert(reminders)
      .values({
        userId: identity.userId,
        sourceNotificationId: notification!.id,
        title: 'retained reminder',
        remindAt: new Date(Date.now() + 86_400_000),
      })
      .returning({ id: reminders.id })
    reminderIds.push(reminder!.id)

    await retention.purgeExpired()
    const notificationAfter = await db
      .select()
      .from(notifications)
      .where(eq(notifications.id, notification!.id))
    const [reminderAfter] = await db
      .select()
      .from(reminders)
      .where(eq(reminders.id, reminder!.id))
    expect(notificationAfter).toHaveLength(0)
    expect(reminderAfter?.sourceNotificationId).toBeNull()
  })

  it('creates a quadrant candidate and completes an existing item from later evidence', async () => {
    const identity = await createIdentity()
    const [notification] = await db
      .insert(notifications)
      .values({
        ...identity,
        notificationKey: `digest-${Date.now()}`,
        packageName: 'test',
        body: 'later segment evidence',
        timezone: 'Asia/Hong_Kong',
        postedAt: new Date(),
      })
      .returning({ id: notifications.id })
    notificationIds.push(notification!.id)
    const [existing] = await db
      .insert(reminders)
      .values({
        userId: identity.userId,
        title: 'Old candidate',
        status: 'candidate',
        remindAt: new Date(Date.now() + 60_000),
      })
      .returning({ id: reminders.id })
    reminderIds.push(existing!.id)

    await analysisRepository.saveDigest(identity.userId, [notification!.id], {
      reminders: [{
        sourceNotificationId: notification!.id,
        title: 'Plan the report',
        reason: 'explicit unfinished request',
        quadrant: 'important_not_urgent',
        remindAt: new Date(Date.now() + 3_600_000).toISOString(),
      }],
      updates: [{
        reminderId: existing!.id,
        action: 'complete',
        reason: 'later notification explicitly says it was submitted',
      }],
    })

    const rows = await db
      .select()
      .from(reminders)
      .where(inArray(reminders.userId, [identity.userId]))
    reminderIds.push(...rows.map((row) => row.id).filter((id) => id !== existing!.id))
    expect(rows.find((row) => row.id === existing!.id)?.status).toBe('done')
    expect(rows.find((row) => row.sourceNotificationId === notification!.id)?.quadrant)
      .toBe('important_not_urgent')
  })
})
