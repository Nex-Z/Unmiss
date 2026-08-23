import 'reflect-metadata'
import { NestFactory } from '@nestjs/core'
import { AppModule } from './app.module'
import { WorkerSchedulerService } from './worker-scheduler.service'

async function bootstrap(): Promise<void> {
  const app = await NestFactory.createApplicationContext(AppModule)
  app.enableShutdownHooks()
  app.get(WorkerSchedulerService).start()
}
void bootstrap()
