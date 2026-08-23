import { INestApplication, ValidationPipe } from '@nestjs/common'
import { Test } from '@nestjs/testing'
import { eq } from 'drizzle-orm'
import request from 'supertest'
import { AppModule } from '../src/app.module'
import { DRIZZLE, type DrizzleDB } from '../src/database/database.module'
import { devices, reminders, users } from '../src/database/schema'

describe('User data isolation (e2e)', () => {
  let app: INestApplication
  let db: DrizzleDB
  let survivorUserId: string
  let survivorReminderId: string

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile()
    app = moduleRef.createNestApplication()
    app.setGlobalPrefix('api/v1')
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }))
    await app.init()
    db = moduleRef.get(DRIZZLE)
  })

  afterAll(async () => {
    if (survivorReminderId) {
      await db.delete(reminders).where(eq(reminders.id, survivorReminderId))
    }
    if (survivorUserId) {
      await db.delete(devices).where(eq(devices.userId, survivorUserId))
      await db.delete(users).where(eq(users.id, survivorUserId))
    }
    await app.close()
  })

  it('deletes only the authenticated user data', async () => {
    const first = await register('delete-target')
    const second = await register('must-survive')
    survivorUserId = second.userId

    const [firstReminder] = await db
      .insert(reminders)
      .values({ userId: first.userId, title: 'delete me', remindAt: new Date() })
      .returning({ id: reminders.id })
    const [secondReminder] = await db
      .insert(reminders)
      .values({ userId: second.userId, title: 'keep me', remindAt: new Date() })
      .returning({ id: reminders.id })
    survivorReminderId = secondReminder!.id

    await request(app.getHttpServer())
      .delete('/api/v1/devices/me/data')
      .set('Authorization', `Bearer ${first.token}`)
      .expect(200)

    await request(app.getHttpServer())
      .get('/api/v1/reminders/pending')
      .set('Authorization', `Bearer ${first.token}`)
      .expect(401)

    const deleted = await db
      .select()
      .from(reminders)
      .where(eq(reminders.id, firstReminder!.id))
    const survivor = await db
      .select()
      .from(reminders)
      .where(eq(reminders.id, secondReminder!.id))
    expect(deleted).toHaveLength(0)
    expect(survivor).toHaveLength(1)
  })

  async function register(name: string): Promise<{ token: string; userId: string }> {
    const response = await request(app.getHttpServer())
      .post('/api/v1/devices/register')
      .send({ name, platform: 'test' })
      .expect(201)
    return {
      token: response.body.token as string,
      userId: response.body.device.userId as string,
    }
  }
})
