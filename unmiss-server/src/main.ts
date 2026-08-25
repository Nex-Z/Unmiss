import 'reflect-metadata'
import { ValidationPipe } from '@nestjs/common'
import { NestFactory } from '@nestjs/core'
import type { NestExpressApplication } from '@nestjs/platform-express'
import { AppModule } from './app.module'
import { WorkerSchedulerService } from './worker-scheduler.service'

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create<NestExpressApplication>(AppModule)
  app.useBodyParser('json', { limit: '2mb' })
  app.useBodyParser('urlencoded', { limit: '64kb', extended: true })
  app.setGlobalPrefix('api/v1')
  app.useGlobalPipes(
    new ValidationPipe({ whitelist: true, transform: true }),
  )
  app.enableShutdownHooks()

  const port = Number(process.env.PORT ?? 3000)
  await app.listen(port)
  app.get(WorkerSchedulerService).start()
}
void bootstrap()
