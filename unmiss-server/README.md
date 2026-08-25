# Unmiss Server

NestJS + Drizzle ORM + PostgreSQL backend for Unmiss (Phase 0-4).

## Stack

- NestJS 11, TypeScript strict
- Drizzle ORM + drizzle-kit migrations
- PostgreSQL 17
- pnpm, Node >= 20

## Getting started

```bash
pnpm install
cp .env.example .env
```

Edit `.env` and set `DATABASE_URL`, `JWT_SECRET`, `AI_MODEL`, and
`AI_BASE_URL`. Provide `DEEPSEEK_API_KEY` through the process or system
environment; do not store it in the repository. `DATABASE_URL` must point to
the dedicated `unmiss` database.

### Migrations

```bash
pnpm db:generate   # generate SQL from schema changes
pnpm db:migrate    # apply migrations (uses DATABASE_URL)
```

### Run

```bash
pnpm build
pnpm test:e2e
pnpm start        # API on :3000 + notification worker
pnpm worker       # optional standalone worker for independent scaling
```

Or full stack:

```bash
docker compose up -d --build
```

## API

Base prefix: `/api/v1`. All routes require `Authorization: Bearer <token>`
except `/api/v1/devices/register` and `/api/v1/health`.

| Method | Path                        | Description |
| ------ | --------------------------- | ----------- |
| GET    | `/api/v1/health`            | health check |
| POST   | `/api/v1/devices/register`  | body `{name?, platform}` → `{token, device}` |
| POST   | `/api/v1/devices/push-token` | register or replace the authenticated device push token |
| DELETE | `/api/v1/devices/me/data` | permanently delete the authenticated user's server data |
| POST   | `/api/v1/notifications`     | body `{deviceId, notificationKey, packageName, title?, body?, subText?, postedAt}`, idempotent via `UNIQUE(device_id, notification_key)` |
| POST   | `/api/v1/notifications/batch` | insert up to 100 notifications in one request; returns `{accepted, created}` |
| GET    | `/api/v1/reminders/pending` | list pending reminders for the authenticated user |
| POST   | `/api/v1/reminders/:id/done` | mark a reminder done |
| POST   | `/api/v1/reminders/:id/snooze` | body `{remindAt}` → reschedule a reminder |
| POST   | `/api/v1/reminders/:id/ignore` | ignore a reminder |

Notifications use `onConflictDoNothing`; batch retries are safe and report how
many rows were accepted versus newly created. HTTP requests return after the
database write, while AI analysis runs asynchronously in the worker.

When `AI_MODEL`, `DEEPSEEK_API_KEY` (or `AI_API_KEY`), and `AI_BASE_URL` are configured, notifications
are analyzed through an OpenAI-compatible `chat/completions` endpoint. The
worker retries notifications whose analysis has not completed. Without these
values, notifications continue to upload normally and no reminders are created.

The worker atomically claims notifications whose AI analysis needs retrying and
removes raw notifications older than `NOTIFICATION_RETENTION_DAYS` (14 by
default). Reminder delivery is scheduled locally by Android WorkManager after
immediate or fallback synchronization. Reminder and event history is retained
when source notification text is removed.

Requests are limited in-process per device/IP (`RATE_LIMIT_PER_MINUTE`, default
120), and HTTP request bodies are limited to 64 KB. Multi-instance deployments
should replace the in-memory limiter with a shared Redis-backed implementation.

## Layout

```
src/
├── main.ts            # API entrypoint
├── worker.ts          # Worker entrypoint
├── app.module.ts      # global JWT guard wired here
├── config/            # env validation (zod)
├── database/          # drizzle module, schema, migrations
├── auth/              # jwt strategy + global guard
├── devices/           # registration + token issuing
├── notifications/     # upload + idempotent upsert
├── agent/             # structured notification analysis + persistence
├── reminders/         # pending/done/snooze/ignore lifecycle
└── common/            # decorators, health controller
```
