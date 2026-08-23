import { z } from 'zod'

export const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
  PORT: z.coerce.number().int().positive().default(3000),
  DATABASE_URL: z.string().min(1),
  POSTGRES_PASSWORD: z.string().default(''),
  JWT_SECRET: z.string().min(32).refine((value) => !value.includes('change_me')),
  AI_PROVIDER: z.string().default(''),
  AI_MODEL: z.string().default(''),
  AI_API_KEY: z.string().default(''),
  DEEPSEEK_API_KEY: z.string().default(''),
  AI_BASE_URL: z.string().default(''),
  NOTIFICATION_RETENTION_DAYS: z.coerce.number().int().min(1).max(365).default(14),
  RATE_LIMIT_PER_MINUTE: z.coerce.number().int().min(10).max(10_000).default(120),
})

export type Env = z.infer<typeof envSchema>
