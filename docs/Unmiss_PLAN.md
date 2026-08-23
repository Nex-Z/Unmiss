# Unmiss — MVP 开发计划

> **一句话定义：** 把“已经看过，但事情还没有结束”的重要通知重新带回来。

---

## 1. 产品目标

Unmiss 是一个 Android-first 的智能提醒应用。

它不解决“通知太多”本身，而解决：

> **用户看过通知以后，真正需要处理的事情很容易随着通知消失而被忘记。**

典型场景：

```text
微信：
张三：那个材料明天下午前发我一下

↓ Unmiss 自动识别

提醒：
明天下午前把材料发给张三

[已完成] [稍后提醒] [忽略]
```

核心闭环：

```text
Android Notification
        ↓
NotificationListenerService
        ↓
Android 本地暂存
        ↓ HTTPS
Backend API
        ↓
PostgreSQL
        ↓
Pi Agent
        ↓
识别是否形成待办 / 提醒时间
        ↓
Reminder Scheduler
        ↓
Push 到 Android
        ↓
[已完成] [稍后提醒] [忽略]
        ↓
Backend 更新状态
```

---

## 2. MVP 成功标准

第一版不追求“万能 AI 助手”，只验证一件事：

> **Unmiss 是否真的避免了用户忘记重要事情。**

建议自己持续使用 1～2 周，重点观察：

1. 有多少 Reminder 真正有帮助。
2. 有多少 Reminder 被用户直接 Ignore。
3. 是否存在重要事项被模型漏掉。
4. Snooze 后是否最终被完成。
5. 用户是否开始依赖这个系统，而不是觉得它制造了更多通知。

第一阶段优先优化：

> **Precision > Recall**

宁愿少提醒，也不要把 Unmiss 做成另一个通知噪音源。

---

# 3. 总体技术架构

## 3.1 强制移动端 / 服务端分离

移动端和服务端必须是两个完全独立的工程。

建议：

```text
unmiss/
├── unmiss-android/
└── unmiss-server/
```

二者只通过 HTTPS API / Push 通信。

不要共享：

- 数据库代码
- Agent runtime
- 业务状态机
- Server-side model
- Scheduler

Android 不直接连接 PostgreSQL。

---

## 3.2 架构图

```text
┌─────────────────────────────────┐
│          Android App            │
│                                 │
│  NotificationListenerService    │
│            │                    │
│            ↓                    │
│       Local Room Queue          │
│            │                    │
│            ↓                    │
│        HTTPS Client             │
│                                 │
│  Reminder Notification Actions  │
│  Done / Snooze / Ignore         │
└──────────────┬──────────────────┘
               │
               │ HTTPS
               ▼
┌─────────────────────────────────┐
│          NestJS Server          │
│                                 │
│  Notification Module            │
│         │                       │
│         ▼                       │
│     PostgreSQL                  │
│         │                       │
│         ▼                       │
│     Pi Agent Module             │
│         │                       │
│         ▼                       │
│     Reminder Module             │
│         │                       │
│         ▼                       │
│    Scheduler / Worker           │
│         │                       │
│         ▼                       │
│      Push Gateway               │
└─────────────────────────────────┘
```

---

# 4. Android 客户端

## 4.1 技术栈

```text
Kotlin
Jetpack Compose
NotificationListenerService
Room
WorkManager
Retrofit 或 Ktor Client
Firebase Cloud Messaging（MVP 推荐）
```

第一版只做 Android。

暂时不做 iOS。

原因：

- Android 提供 NotificationListenerService。
- iOS 无法以相同方式读取系统内其他 App 的所有通知。
- 本项目的核心能力高度依赖 Android 系统 API。

---

## 4.2 Android 职责

Android 只负责：

```text
采集通知
↓
本地可靠暂存
↓
上传 Backend
↓
接收 Backend Push
↓
展示 Reminder
↓
接收用户操作
↓
同步 Done / Snooze / Ignore
```

**不要在 Android 内运行 Pi Agent。**

**不要把核心 AI 决策放在 Android。**

---

# 5. Notification Listener

核心 Service：

```kotlin
class UnmissNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 1. 过滤来源
        // 2. 提取通知数据
        // 3. 写入 Room
        // 4. 触发上传
    }
}
```

建议提取字段：

```text
notification_key
package_name
title
text
sub_text
post_time
device_id
```

第一版不上传：

```text
RemoteViews
应用内部不可见数据
```

---

# 6. App Allowlist

默认不要上传所有通知。

第一版使用 Allowlist：

```text
常用即时通讯软件
短信、邮箱
其他用户主动选择的 App
```

用户必须明确决定手机上已安装的哪些 App 可以进入 Unmiss。

数据结构：

```text
allowed_apps
------------
package_name
display_name
enabled
```

---

# 7. Android 本地缓存

Android 使用 Room。

Room **不是主数据库**，只作为：

> Offline Queue / Upload Buffer

建议表：

```text
pending_notification_uploads
----------------------------
id
notification_key
payload_json
created_at
uploaded_at
retry_count
last_error
```

发送流程：

```text
收到通知
↓
立即写 Room
↓
尝试 POST backend
↓
成功
    → 标记 uploaded
失败
    → WorkManager 重试
```

这样即使：

- 手机临时断网
- Backend 重启
- DNS 失败
- 请求 timeout

也不会直接丢通知。

---

# 8. Server 技术栈

## 8.1 Node.js Framework

服务端使用：

> **NestJS + TypeScript**

原因：

- 模块化清晰。
- Controller / Service / Module 边界明确。
- Dependency Injection 适合 Agent / DB / Scheduler。
- 后续扩展 Worker、Queue、WebSocket、Auth 都方便。
- 比裸 Express 更适合作为长期产品后端。

建议：

```text
Node.js >= 20
TypeScript
NestJS
```

HTTP Adapter 第一版直接使用 Nest 默认 Express。

暂时不需要为了性能切 Fastify。

---

# 9. Server 项目结构

建议：

```text
unmiss-server/
├── src/
│   ├── main.ts
│   ├── app.module.ts
│   │
│   ├── config/
│   │   ├── config.module.ts
│   │   └── env.schema.ts
│   │
│   ├── database/
│   │   ├── database.module.ts
│   │   └── migrations/
│   │
│   ├── devices/
│   │   ├── devices.controller.ts
│   │   ├── devices.service.ts
│   │   └── devices.module.ts
│   │
│   ├── notifications/
│   │   ├── notifications.controller.ts
│   │   ├── notifications.service.ts
│   │   ├── notifications.repository.ts
│   │   └── notifications.module.ts
│   │
│   ├── reminders/
│   │   ├── reminders.controller.ts
│   │   ├── reminders.service.ts
│   │   ├── reminders.repository.ts
│   │   └── reminders.module.ts
│   │
│   ├── agent/
│   │   ├── agent.module.ts
│   │   ├── reminder-agent.service.ts
│   │   ├── prompts/
│   │   └── tools/
│   │
│   ├── scheduler/
│   │   ├── scheduler.module.ts
│   │   └── reminder.worker.ts
│   │
│   ├── push/
│   │   ├── push.module.ts
│   │   └── push.service.ts
│   │
│   └── common/
│
├── test/
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── package.json
├── tsconfig.json
└── README.md
```

---

# 10. PostgreSQL

服务端数据库直接使用 PostgreSQL。

不使用 SQLite 作为 Server 主数据库。

理由：

- Reminder 本身具有状态。
- Scheduler 需要查询到期任务。
- 后续可能有多个 Worker。
- 用户反馈需要长期存储。
- Agent 需要查询历史通知。
- 后续可以使用事务和 `FOR UPDATE SKIP LOCKED`。

---

# 11. ORM / Database Layer

建议使用：

> **Drizzle ORM**

如果更熟 Prisma，也可以使用 Prisma。

Plan 默认：

```text
PostgreSQL
+
Drizzle ORM
+
drizzle-kit migrations
```

原则：

> Agent 不直接写 SQL。

Pi tools 调用 Repository / Service。

---

# 12. PostgreSQL 数据模型

## users

MVP 即使只有自己，也建议保留 user 这一层。

```sql
users (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
)
```

---

## devices

```sql
devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name TEXT,
    platform TEXT NOT NULL,
    push_token TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ
)
```

---

## notifications

```sql
notifications (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    device_id UUID NOT NULL,

    notification_key TEXT,
    package_name TEXT NOT NULL,

    title TEXT,
    body TEXT,
    sub_text TEXT,

    posted_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,

    agent_processed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL
)
```

推荐唯一索引：

```sql
UNIQUE(device_id, notification_key)
```

防止 Android retry 造成重复数据。

---

## reminders

状态建议：

```text
pending
done
ignored
```

Snooze 不需要成为永久状态。

Snooze 本质只是：

```text
status = pending
remind_at = 新时间
```

表：

```sql
reminders (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    source_notification_id UUID,

    title TEXT NOT NULL,
    description TEXT,

    reason TEXT,

    importance SMALLINT,

    status TEXT NOT NULL,

    remind_at TIMESTAMPTZ NOT NULL,

    last_shown_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
)
```

---

## reminder_events

任何用户行为都记录 event。

```sql
reminder_events (
    id UUID PRIMARY KEY,

    reminder_id UUID NOT NULL,

    type TEXT NOT NULL,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL
)
```

event 类型：

```text
created
shown
done
ignored
snoozed
```

以后做 personalization 时，这张表非常重要。

---

# 13. Pi Agent

服务端 AI 使用：

```text
@mariozechner/pi-ai
@mariozechner/pi-agent-core
```

Pi 是 Backend 内部模块。

不要直接把 Pi session 暴露为 HTTP API。

结构：

```text
NestJS
  ↓
ReminderAgentService
  ↓
Pi Agent
  ↓
Tools
  ↓
NestJS Services / Repositories
```

mvp至少能能接入like openai的大模型服务商
---

# 14. Pi 的职责

Pi 负责：

> **理解 notification 背后是否存在尚未完成的 obligation。**

它不负责：

- Scheduler
- Push
- 数据库连接生命周期
- HTTP API
- Android 通信
- retry
- cron
- Authentication

这些全部属于普通 backend。

---

# 15. 第一版 Pi Tools

保持极少。

建议：

```text
getRecentNotifications()
getPendingReminders()
createReminder()
```

第一阶段甚至可以只给：

```text
createReminder()
```

避免 Agent 做过多自主操作。

---

# 16. Agent 输入

输入示例：

```json
{
  "notification": {
    "package": "com.tencent.mm",
    "title": "张三",
    "text": "那个材料明天下午之前发我一下",
    "postedAt": "2026-08-22T14:00:00Z"
  },
  "timezone": "Asia/Shanghai"
}
```

---

# 17. Agent 判断目标

System Prompt 的核心问题：

```text
判断该通知是否意味着：

用户未来仍需要采取一个动作，
并且该动作可能因为通知消失而被遗忘。
```

优先识别：

```text
需要回复
需要提交
需要发送
需要付款
需要购买
需要预约
需要取消
需要确认
需要跟进
明确 deadline
别人等待用户处理
用户作出的承诺
```

通常忽略：

```text
广告
新闻
促销
点赞
纯 FYI
普通群聊
系统运行状态
下载完成
内容推荐
无行动需求的信息
```

---

# 18. Agent Output

Pi 最终必须产生结构化结果。

逻辑结果：

```json
{
  "shouldCreateReminder": true,
  "title": "把材料发给张三",
  "reason": "张三明确要求在明天下午前收到材料",
  "importance": 4,
  "remindAt": "2026-08-23T13:00:00+08:00"
}
```

如果不需要：

```json
{
  "shouldCreateReminder": false,
  "reason": "普通信息通知，无后续行动要求"
}
```

---

# 19. Agent 设计原则

不要让 Pi 成为整个系统。

正确关系：

```text
Product System
    │
    ├── Database
    ├── Scheduler
    ├── Push
    ├── API
    └── Pi Agent
```

不是：

```text
Pi Agent
   └── everything
```

这样未来可以：

```text
Pi
↓
其他 Agent Framework
↓
普通 LLM structured output
```

而不影响整个产品。

---

# 20. Reminder Scheduler

第一版直接使用 NestJS Scheduler 或独立 Worker。

推荐设计：

```text
API Process
+
Worker Process
```

代码可以在同一个 NestJS repository。

部署时运行两个 container：

```text
unmiss-api
unmiss-worker
```

这样 API 和 background job 生命周期分离。

---

# 21. Worker 查询

例如每 30 秒查询一次：

```sql
SELECT *
FROM reminders
WHERE status = 'pending'
  AND remind_at <= now()
  AND (
      last_shown_at IS NULL
      OR last_shown_at < remind_at
  )
ORDER BY remind_at
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

然后：

```text
取得 Reminder
↓
发送 Push
↓
更新 last_shown_at
↓
写 reminder_event(shown)
```

---

# 22. Push

第一版推荐：

> Firebase Cloud Messaging

流程：

```text
NestJS Worker
↓
FCM
↓
Android
↓
Android 创建本地 Notification
```

Push payload 中只传：

```text
reminder_id
title
body
```

---

# 23. Reminder Notification

示例：

```text
Unmiss

别忘了：
明天下午前把材料发给张三

[已完成] [1小时后] [忽略]
```

Action：

### 已完成

调用：

```http
POST /v1/reminders/:id/done
```

Backend：

```text
status = done
completed_at = now()
```

---

### 稍后提醒

第一版固定：

```text
1 小时后
今晚
明天
```

调用：

```http
POST /v1/reminders/:id/snooze
```

body：

```json
{
  "remindAt": "..."
}
```

Backend：

```text
status = pending
remind_at = new_time
```

并写：

```text
reminder_event = snoozed
```

---

### 忽略

```http
POST /v1/reminders/:id/ignore
```

Backend：

```text
status = ignored
```

并记录 event。

---

# 24. REST API

建议 API prefix：

```text
/api/v1
```

---

## Device

```http
POST /api/v1/devices/register
```

---

## Notification

```http
POST /api/v1/notifications
```

Android 上传 notification。

要求 API 支持 idempotency。

---

## Reminder

```http
GET /api/v1/reminders
GET /api/v1/reminders/pending

POST /api/v1/reminders/:id/done
POST /api/v1/reminders/:id/ignore
POST /api/v1/reminders/:id/snooze
```

---

# 25. Docker

Server 必须可以完全通过 Docker 部署。

需要提供：

```text
Dockerfile
docker-compose.yml
.env.example
```

---

# 26. Dockerfile

推荐 multi-stage：

```dockerfile
FROM node:22-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build


FROM node:22-alpine AS runtime

WORKDIR /app

ENV NODE_ENV=production

COPY package*.json ./
RUN npm ci --omit=dev

COPY --from=builder /app/dist ./dist

CMD ["node", "dist/main.js"]
```

如果使用 pnpm，则统一改成 pnpm。

不要混用 npm / pnpm。

---

# 27. Docker Compose

目标：

```text
docker compose up -d
```

即可启动完整 backend。

建议：

```yaml
services:

  postgres:
    image: postgres:17
    restart: unless-stopped

    environment:
      POSTGRES_DB: unmiss
      POSTGRES_USER: unmiss
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}

    volumes:
      - postgres_data:/var/lib/postgresql/data

    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U unmiss"]
      interval: 5s
      timeout: 5s
      retries: 10


  api:
    build: .
    restart: unless-stopped

    command: node dist/main.js

    env_file:
      - .env

    depends_on:
      postgres:
        condition: service_healthy

    ports:
      - "3000:3000"


  worker:
    build: .
    restart: unless-stopped

    command: node dist/worker.js

    env_file:
      - .env

    depends_on:
      postgres:
        condition: service_healthy


volumes:
  postgres_data:
```

---

# 28. Environment Variables

`.env.example`：

```env
NODE_ENV=production

PORT=3000

DATABASE_URL=postgresql://unmiss:password@postgres:5432/unmiss

POSTGRES_PASSWORD=change_me

AI_PROVIDER=
AI_MODEL=
AI_API_KEY=

FCM_PROJECT_ID=
FCM_CLIENT_EMAIL=
FCM_PRIVATE_KEY=

JWT_SECRET=
```

所有 Secret 禁止进入 Git。

---

# 29. Production 部署

第一版可以直接：

```text
VPS
+
Docker
+
Docker Compose
+
Caddy / Nginx
```

推荐：

```text
Internet
   ↓
Caddy
   ↓ HTTPS
unmiss-api
   ↓
PostgreSQL
```

PostgreSQL 不暴露公网端口。

Worker 同样不暴露端口。

---

# 30. 安全

通知数据属于高度敏感信息。

MVP 就需要：

```text
HTTPS only
App allowlist
DB 最小权限
不记录 notification body 到普通 application log
禁止 debug log 输出完整通知
API authentication
Device identity
Rate limiting
Request size limit
```

---

# 31. Retention

原始 notification 不应该无限保存。

建议：

```text
notifications:
7～30 天自动清理

reminders:
长期保存

reminder_events:
长期保存
```

以后做用户偏好学习时，主要使用：

```text
Reminder
+
Reminder Event
```

而不是永久存储所有聊天通知。

---

# 32. Privacy Controls

Android 设置页至少提供：

```text
允许监听的 App
暂停 Unmiss
查看 Pending Reminders
清空 Notification History
删除所有 Server Data
```

以后再考虑：

```text
端到端加密
本地敏感数据预过滤
敏感 App blacklist
本地小模型初筛
```

---

# 33. MVP 不做的东西

明确控制 Scope。

第一版不做：

```text
iOS
网页聊天 UI
Todo 管理系统
日历同步
自动回复消息
自动执行任务
多 Agent
复杂 Memory
Vector DB
RAG
Embedding
通知摘要
每日总结
聊天记录同步
语音
桌面客户端
```

---

# 34. 开发阶段

## Phase 0 — Repo

```text
[ ] 创建 unmiss-android
[ ] 创建 unmiss-server
[ ] Server 初始化 NestJS
[ ] Dockerfile
[ ] docker-compose.yml
[ ] PostgreSQL 可以启动
[ ] NestJS 可以连 PostgreSQL
```

验收：

```bash
docker compose up -d
```

Server health check 正常。

---

## Phase 1 — Android Notification Capture

```text
[ ] Compose 空项目
[ ] NotificationListenerService
[ ] Notification Access 权限引导
[ ] 读取 package/title/text
[ ] App allowlist
[ ] Room queue
```

验收：

> 手机上收到消息后，Room 能看到 notification。

---

## Phase 2 — Notification Upload

```text
[ ] POST /notifications
[ ] Device registration
[ ] Android HTTP Client
[ ] WorkManager retry
[ ] Backend idempotency
[ ] PostgreSQL notifications table
```

验收：

> 手机收到 notification 后，PG 中稳定产生唯一记录。

---

## Phase 3 — Pi

```text
[ ] 安装 @mariozechner/pi-ai
[ ] 安装 @mariozechner/pi-agent-core
[ ] AgentModule
[ ] ReminderAgentService
[ ] Prompt
[ ] Structured output
[ ] createReminder tool
```

验收：

输入：

```text
明天下午之前把材料发给我
```

得到：

```text
Reminder:
把材料发给 XXX
```

输入广告通知时不创建 Reminder。

---

## Phase 4 — Reminder DB

```text
[ ] reminders
[ ] reminder_events
[ ] create reminder
[ ] pending list
[ ] done
[ ] ignore
[ ] snooze
```

验收：

Reminder 生命周期完整。

---

## Phase 5 — Scheduler

> 当前实现：Worker 每 5 秒扫描，使用 `FOR UPDATE SKIP LOCKED` 并发领取，更新
> `last_shown_at` 并写入 `due` event。

```text
[ ] Nest Worker entrypoint
[ ] 查询 due reminders
[ ] FOR UPDATE SKIP LOCKED
[ ] 标记 shown
[ ] reminder_events
```

验收：

remind_at 到达以后，Worker 能准确触发一次。

---

## Phase 6 — Push

> 当前实现：Android 每 15 分钟同步 pending reminders，并通过独立 WorkManager
> 在本地准时展示，作为推送兜底；设备 push token 注册 API 已就绪。FCM 实际发送
> 需要项目凭据，留待统一验证阶段接入。

```text
[ ] FCM
[ ] Device push token
[ ] Backend push service
[ ] Android receiver
[ ] Reminder notification
```

验收：

Server 可以让手机弹出：

```text
别忘了 XXX
```

---

## Phase 7 — Notification Actions

> 当前实现：Android 通知栏支持 Done、延后一小时、Ignore，操作由 WorkManager
> 在联网后可靠同步服务端。

```text
[ ] Done Action
[ ] Snooze Action
[ ] Ignore Action
[ ] BroadcastReceiver
[ ] Backend sync
```

验收：

用户无需打开 App 就能处理提醒。

---

## Phase 8 — Dogfooding

连续使用至少 1～2 周。

只记录：

```text
提醒总数
Done 数
Ignore 数
Snooze 数
漏提醒案例
误提醒案例
```

重点观察：

```text
ignore rate
useful reminder rate
missed obligation rate
```

---

# 35. 后续可能升级 Pi 的地方

第一版：

```text
单 notification
↓
Agent 判断
↓
createReminder
```

以后可以增加：

```text
notification
↓
Pi
├── getRecentNotifications
├── getPendingReminders
├── queryCalendar
├── findDuplicateReminder
└── create/update reminder
```

例如：

```text
A:
你今晚把那个 PR review 一下

30 分钟后 B:
PR 已经 merge 了
```

Pi 可以查询最近上下文，然后决定：

```text
不要创建 Reminder
```

这时 Agent runtime 才真正开始产生价值。

---

# 36. Agent 不应该拥有的能力

第一阶段禁止 Pi：

```text
任意 Shell
任意 SQL
任意 HTTP 请求
直接发 Push
直接删 DB 数据
任意文件访问
```

只暴露白名单 Tools。

例如：

```text
getRecentNotifications
getPendingReminders
createReminder
```

工具本身由 NestJS 做：

```text
鉴权
校验
事务
数据库访问
```

---

# 37. 推荐最终 Stack

## Android

```text
Kotlin
Jetpack Compose
NotificationListenerService
Room
WorkManager
Retrofit
FCM
```

## Server

```text
Node.js
TypeScript
NestJS
Pi Agent Core
Pi AI
Drizzle ORM
PostgreSQL
```

## Infrastructure

```text
Docker
Docker Compose
Caddy
VPS
```

---

# 38. 最终原则

整个产品保持下面这个边界：

```text
Android = 感知 + 交互

NestJS = 产品系统

PostgreSQL = 状态

Pi = 判断 / reasoning

Worker = 时间

FCM = 回到用户
```

不要把状态、调度和基础设施塞进 Agent。

**Agent 是 Unmiss 的智能能力，不是 Unmiss 本身。**

---

# 39. 第一版真正要做的事情

如果要马上开写，只关注这一条 vertical slice：

```text
收到 Android Notification
        ↓
成功上传 Backend
        ↓
存入 PostgreSQL
        ↓
Pi 判断需要提醒
        ↓
写入 Reminder
        ↓
Worker 到时间触发
        ↓
Android 弹提醒
        ↓
用户点 Done
        ↓
Server 状态变成 done
```

这条链跑通，Unmiss 的 MVP 就已经成立。

其他功能都可以之后再加。
