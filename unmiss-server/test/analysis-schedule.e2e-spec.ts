import { INestApplication, ValidationPipe } from '@nestjs/common'
import { Test } from '@nestjs/testing'
import { eq } from 'drizzle-orm'
import request from 'supertest'
import { AppModule } from '../src/app.module'
import { DRIZZLE, type DrizzleDB } from '../src/database/database.module'
import { devices, users } from '../src/database/schema'

describe('Analysis schedule (e2e)', () => {
  let app: INestApplication
  let db: DrizzleDB
  let token: string
  let userId: string

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
  })

  afterAll(async () => {
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
})
