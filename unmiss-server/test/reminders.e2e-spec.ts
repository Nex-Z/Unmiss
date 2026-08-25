import { INestApplication, ValidationPipe } from '@nestjs/common'
import { Test } from '@nestjs/testing'
import { eq, inArray } from 'drizzle-orm'
import request from 'supertest'
import { AppModule } from '../src/app.module'
import { DRIZZLE, type DrizzleDB } from '../src/database/database.module'
import {
  devices,
  reminderEvents,
  reminders,
  users,
} from '../src/database/schema'

describe('Reminders (e2e)', () => {
  let app: INestApplication
  let db: DrizzleDB
  let token: string
  let userId: string
  const reminderIds: string[] = []

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile()
    app = moduleRef.createNestApplication()
    app.setGlobalPrefix('api/v1')
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }))
    await app.init()
    db = moduleRef.get(DRIZZLE)

    const registration = await request(app.getHttpServer())
      .post('/api/v1/devices/register')
      .send({ name: 'e2e-reminders', platform: 'test' })
      .expect(201)
    token = registration.body.token as string
    userId = registration.body.device.userId as string

    const inserted = await db
      .insert(reminders)
      .values([
        { userId, title: 'First test reminder', remindAt: new Date(Date.now() + 60_000) },
        { userId, title: 'Second test reminder', remindAt: new Date(Date.now() + 120_000) },
      ])
      .returning({ id: reminders.id })
    reminderIds.push(...inserted.map((row) => row.id))
  })

  afterAll(async () => {
    if (reminderIds.length > 0) {
      await db.delete(reminderEvents).where(inArray(reminderEvents.reminderId, reminderIds))
      await db.delete(reminders).where(inArray(reminders.id, reminderIds))
    }
    if (userId) {
      await db.delete(devices).where(eq(devices.userId, userId))
      await db.delete(users).where(eq(users.id, userId))
    }
    await app.close()
  })

  it('requires authentication', async () => {
    await request(app.getHttpServer()).get('/api/v1/reminders/pending').expect(401)
  })

  it('supports pending, snooze, done and ignore', async () => {
    const auth = { Authorization: `Bearer ${token}` }
    const pending = await request(app.getHttpServer())
      .get('/api/v1/reminders/pending')
      .set(auth)
      .expect(200)
    expect(pending.body).toHaveLength(2)

    const snoozedAt = new Date(Date.now() + 3_600_000).toISOString()
    const snoozed = await request(app.getHttpServer())
      .post(`/api/v1/reminders/${reminderIds[0]}/snooze`)
      .set(auth)
      .send({ remindAt: snoozedAt })
      .expect(201)
    expect(snoozed.body.status).toBe('pending')

    const done = await request(app.getHttpServer())
      .post(`/api/v1/reminders/${reminderIds[0]}/done`)
      .set(auth)
      .expect(201)
    expect(done.body.status).toBe('done')

    await request(app.getHttpServer())
      .post(`/api/v1/reminders/${reminderIds[0]}/done`)
      .set(auth)
      .expect(201)

    await request(app.getHttpServer())
      .post(`/api/v1/reminders/${reminderIds[0]}/snooze`)
      .set(auth)
      .send({ remindAt: snoozedAt })
      .expect(409)

    const ignored = await request(app.getHttpServer())
      .post(`/api/v1/reminders/${reminderIds[1]}/ignore`)
      .set(auth)
      .expect(201)
    expect(ignored.body.status).toBe('ignored')

    const empty = await request(app.getHttpServer())
      .get('/api/v1/reminders/pending')
      .set(auth)
      .expect(200)
    expect(empty.body).toEqual([])
  })

  it('keeps digest candidates silent until the user confirms them', async () => {
    const [candidate] = await db
      .insert(reminders)
      .values({
        userId,
        title: 'Possible missed item',
        status: 'candidate',
        remindAt: new Date(Date.now() + 60_000),
      })
      .returning({ id: reminders.id })
    reminderIds.push(candidate!.id)
    const auth = { Authorization: `Bearer ${token}` }

    const pending = await request(app.getHttpServer())
      .get('/api/v1/reminders/pending')
      .set(auth)
      .expect(200)
    expect(pending.body).toEqual([])

    const inbox = await request(app.getHttpServer())
      .get('/api/v1/reminders/inbox')
      .set(auth)
      .expect(200)
    expect(inbox.body).toHaveLength(1)
    expect(inbox.body[0].status).toBe('candidate')

    const confirmed = await request(app.getHttpServer())
      .post(`/api/v1/reminders/${candidate!.id}/confirm`)
      .set(auth)
      .send({ remindAt: new Date(Date.now() + 3_600_000).toISOString() })
      .expect(201)
    expect(confirmed.body.status).toBe('pending')
  })
})
