import { drizzle } from 'drizzle-orm/node-postgres'
import type { NodePgDatabase } from 'drizzle-orm/node-postgres'
import { Pool } from 'pg'
import { ConfigService } from '@nestjs/config'
import { Global, Inject, Module, OnModuleDestroy, Provider } from '@nestjs/common'
import * as schema from './schema'

export const DRIZZLE = Symbol('DRIZZLE')

export type DrizzleDB = NodePgDatabase<typeof schema>

const drizzleProvider: Provider = {
  provide: DRIZZLE,
  inject: [ConfigService],
  useFactory: (configService: ConfigService): DrizzleDB => {
    const connectionString = configService.getOrThrow<string>('DATABASE_URL')
    const pool = new Pool({ connectionString })
    return drizzle(pool, { schema })
  },
}

@Global()
@Module({
  providers: [drizzleProvider],
  exports: [drizzleProvider],
})
export class DatabaseModule implements OnModuleDestroy {
  constructor(@Inject(DRIZZLE) private readonly db: DrizzleDB) {}

  async onModuleDestroy(): Promise<void> {
    const client = (this.db as unknown as { $client: Pool }).$client
    await client.end()
  }
}
