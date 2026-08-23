import { INestApplication } from '@nestjs/common'
import { Test } from '@nestjs/testing'
import request from 'supertest'
import { AppModule } from '../src/app.module'

describe('Rate limit (e2e)', () => {
  let app: INestApplication

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile()
    app = moduleRef.createNestApplication()
    app.setGlobalPrefix('api/v1')
    await app.init()
  })

  afterAll(async () => app.close())

  it('rejects requests beyond the per-minute limit', async () => {
    for (let index = 0; index < 120; index += 1) {
      await request(app.getHttpServer()).get('/api/v1/health').expect(200)
    }
    await request(app.getHttpServer()).get('/api/v1/health').expect(429)
  })
})
