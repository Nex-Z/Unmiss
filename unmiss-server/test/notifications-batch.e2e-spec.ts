import { INestApplication, ValidationPipe } from '@nestjs/common'
import { Test } from '@nestjs/testing'
import { eq } from 'drizzle-orm'
import request from 'supertest'
import { AppModule } from '../src/app.module'
import { DRIZZLE, type DrizzleDB } from '../src/database/database.module'
import { devices, notifications, users } from '../src/database/schema'

describe('Notification batch upload (e2e)', () => {
  let app: INestApplication
  let db: DrizzleDB
  let userId: string
  let deviceId: string
  let token: string

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile()
    app = moduleRef.createNestApplication()
    app.setGlobalPrefix('api/v1')
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }))
    await app.init()
    db = moduleRef.get(DRIZZLE)

    const response = await request(app.getHttpServer())
      .post('/api/v1/devices/register')
      .send({ name: 'batch-test', platform: 'test' })
      .expect(201)
    userId = response.body.device.userId as string
    deviceId = response.body.device.id as string
    token = response.body.token as string
  })

  afterAll(async () => {
    if (deviceId) await db.delete(notifications).where(eq(notifications.deviceId, deviceId))
    if (userId) {
      await db.delete(devices).where(eq(devices.userId, userId))
      await db.delete(users).where(eq(users.id, userId))
    }
    await app.close()
  })

  it('inserts a batch and treats repeated notification keys as accepted duplicates', async () => {
    const now = new Date().toISOString()
    const payload = {
      notifications: [
        notification('batch-one', now),
        notification('batch-two', now),
      ],
    }
    const first = await request(app.getHttpServer())
      .post('/api/v1/notifications/batch')
      .set('Authorization', `Bearer ${token}`)
      .send(payload)
      .expect(201)
    expect(first.body).toEqual({ accepted: 2, created: 2 })

    const repeated = await request(app.getHttpServer())
      .post('/api/v1/notifications/batch')
      .set('Authorization', `Bearer ${token}`)
      .send(payload)
      .expect(201)
    expect(repeated.body).toEqual({ accepted: 2, created: 0 })
  })

  it('rejects batches larger than 100 notifications', async () => {
    const now = new Date().toISOString()
    await request(app.getHttpServer())
      .post('/api/v1/notifications/batch')
      .set('Authorization', `Bearer ${token}`)
      .send({
        notifications: Array.from({ length: 101 }, (_, index) =>
          notification(`oversize-${index}`, now)),
      })
      .expect(400)
  })

  function notification(key: string, postedAt: string) {
    return {
      deviceId,
      notificationKey: key,
      packageName: 'test.batch',
      title: 'Batch test',
      body: 'Queued notification',
      postedAt,
      timezone: 'Asia/Hong_Kong',
    }
  }
})
