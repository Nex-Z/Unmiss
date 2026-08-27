import { INestApplication, ValidationPipe } from '@nestjs/common'
import { Test } from '@nestjs/testing'
import { eq, inArray } from 'drizzle-orm'
import request from 'supertest'
import { AppModule } from '../src/app.module'
import { DRIZZLE, type DrizzleDB } from '../src/database/database.module'
import {
  analysisRuns,
  devices,
  notifications,
  reminderEvents,
  reminders,
  users,
} from '../src/database/schema'

describe('Analysis schedule (e2e)', () => {
  let app: INestApplication
  let db: DrizzleDB
  let token: string
  let userId: string
  let deviceId: string
  const qualityReminderIds: string[] = []

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile()
    app = moduleRef.createNestApplication()
    app.setGlobalPrefix('api/v1')
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }))
    await app.init()
    db = moduleRef.get(DRIZZLE)

    const response = await request(app.getHttpServer())
      .post('/api/v1/devices/register')
      .send({ name: 'schedule-test', platform: 'test' })
      .expect(201)
    token = response.body.token as string
    userId = response.body.device.userId as string
    deviceId = response.body.device.id as string
  })

  afterAll(async () => {
    if (qualityReminderIds.length > 0) {
      await db.delete(reminderEvents).where(inArray(reminderEvents.reminderId, qualityReminderIds))
      await db.delete(reminders).where(inArray(reminders.id, qualityReminderIds))
    }
    await db.delete(notifications).where(eq(notifications.userId, userId))
    await db.delete(analysisRuns).where(eq(analysisRuns.userId, userId))
    await db.delete(devices).where(eq(devices.userId, userId))
    await db.delete(users).where(eq(users.id, userId))
    await app.close()
  })

  it('stores several sorted local digest times', async () => {
    await request(app.getHttpServer())
      .put('/api/v1/devices/me/analysis-schedule')
      .set('Authorization', `Bearer ${token}`)
      .send({
        times: ['22:00', '07:00', '19:30', '22:00'],
        timezone: 'Asia/Hong_Kong',
      })
      .expect(200)
      .expect(({ body }) => {
        expect(body.times).toEqual(['07:00', '19:30', '22:00'])
        expect(body.timezone).toBe('Asia/Hong_Kong')
      })

    await request(app.getHttpServer())
      .get('/api/v1/devices/me/analysis-schedule')
      .set('Authorization', `Bearer ${token}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body.times).toEqual(['07:00', '19:30', '22:00'])
      })
  })

  it('rejects an invalid timezone', async () => {
    await request(app.getHttpServer())
      .put('/api/v1/devices/me/analysis-schedule')
      .set('Authorization', `Bearer ${token}`)
      .send({ times: ['22:00'], timezone: 'Mars/Olympus' })
      .expect(400)
  })

  it('stores explicit category weights including a disabled category', async () => {
    const weights = {
      work: 5,
      life: 4,
      finance: 3,
      health: 5,
      social: 2,
      entertainment: 0,
      other: 1,
    }
    await request(app.getHttpServer())
      .put('/api/v1/devices/me/category-weights')
      .set('Authorization', `Bearer ${token}`)
      .send(weights)
      .expect(200)
      .expect(weights)

    await request(app.getHttpServer())
      .get('/api/v1/devices/me/category-weights')
      .set('Authorization', `Bearer ${token}`)
      .expect(200)
      .expect(weights)

    await request(app.getHttpServer())
      .put('/api/v1/devices/me/category-weights')
      .set('Authorization', `Bearer ${token}`)
      .send({ ...weights, work: 6 })
      .expect(400)
  })

  it('returns persisted digest attempts', async () => {
    await db.insert(analysisRuns).values({
      userId,
      status: 'failed',
      notificationCount: 41,
      error: 'timeout',
      completedAt: new Date(),
    })

    await request(app.getHttpServer())
      .get('/api/v1/analysis/runs')
      .set('Authorization', `Bearer ${token}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body).toHaveLength(1)
        expect(body[0]).toMatchObject({ status: 'failed', notificationCount: 41 })
      })
  })

  it('summarizes recent quality signals without exposing notification content', async () => {
    await db.insert(analysisRuns).values({
      userId,
      status: 'success',
      notificationCount: 12,
      reminderCount: 2,
      completedAt: new Date(),
    })
    const sourceRows = await db
      .insert(notifications)
      .values([
        {
          userId,
          deviceId,
          notificationKey: 'quality-source-1',
          packageName: 'com.example.chat',
          body: 'sensitive body must not be returned',
          timezone: 'Asia/Hong_Kong',
          postedAt: new Date(),
        },
        {
          userId,
          deviceId,
          notificationKey: 'quality-source-2',
          packageName: 'com.example.chat',
          timezone: 'Asia/Hong_Kong',
          postedAt: new Date(),
        },
      ])
      .returning({ id: notifications.id })
    const reminderRows = await db
      .insert(reminders)
      .values([
        {
          userId,
          sourceNotificationId: sourceRows[0]!.id,
          title: 'Useful item',
          status: 'done',
          remindAt: new Date(Date.now() + 60_000),
        },
        {
          userId,
          sourceNotificationId: sourceRows[1]!.id,
          title: 'Noisy item',
          status: 'ignored',
          remindAt: new Date(Date.now() + 60_000),
        },
      ])
      .returning({ id: reminders.id })
    qualityReminderIds.push(...reminderRows.map((row) => row.id))
    await db.insert(reminderEvents).values([
      { reminderId: reminderRows[0]!.id, type: 'confirmed' },
      { reminderId: reminderRows[0]!.id, type: 'done' },
      { reminderId: reminderRows[0]!.id, type: 'snoozed' },
      { reminderId: reminderRows[1]!.id, type: 'ignored' },
    ])

    await request(app.getHttpServer())
      .get('/api/v1/analysis/quality')
      .set('Authorization', `Bearer ${token}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body.periodDays).toBe(14)
        expect(body.analysis).toMatchObject({ notificationsAnalyzed: 12 })
        expect(body.reminders).toMatchObject({
          created: 2,
          completed: 1,
          ignored: 1,
          confirmed: 1,
          snoozed: 1,
          evaluated: 2,
          usefulRate: 0.5,
          ignoreRate: 0.5,
        })
        expect(body.packages).toEqual([
          { packageName: 'com.example.chat', created: 2, completed: 1, ignored: 1 },
        ])
        expect(JSON.stringify(body)).not.toContain('sensitive body')
      })
  })
})
