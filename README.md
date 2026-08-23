# Unmiss

> **把"已经看过，但事情还没有结束"的重要通知重新带回来。**

Unmiss 是一个 Android-first 的智能提醒应用。它不解决"通知太多"，而是解决：**用户看过通知以后，真正需要处理的事情很容易随着通知消失而被忘记。**

```text
微信：
张三：那个材料明天下午前发我一下

↓ Unmiss 自动识别

提醒：
明天下午前把材料发给张三

[已完成] [稍后提醒] [忽略]
```

完整的产品需求与架构设计见 [docs/Unmiss_PLAN.md](docs/Unmiss_PLAN.md)。

---

## 项目结构

本仓库包含两个完全独立的工程，二者只通过 HTTPS API / WebSocket 通信：

| 目录 | 说明 | 技术栈 |
|---|---|---|
| [`unmiss-android/`](unmiss-android/) | Android 客户端（采集通知、展示提醒） | Kotlin + Jetpack Compose + Room + WorkManager |
| [`unmiss-server/`](unmiss-server/) | 后端服务（存储、AI 判断、提醒状态） | NestJS + TypeScript + Drizzle ORM + PostgreSQL |

核心边界：

```text
Android = 感知 + 交互
NestJS  = 产品系统
PostgreSQL = 状态
DeepSeek = 判断 / reasoning
Worker   = AI 失败重试 + 原文清理
WorkManager = 同步 + 本地定时提醒
```

## 工作原理

```text
Android Notification
    ↓ NotificationListenerService（按 Allowlist 过滤）
Room 本地队列（离线可靠暂存，WorkManager 自动重试）
    ↓ HTTPS
NestJS API（幂等入库 PostgreSQL）
    ↓
DeepSeek V4 Flash 判断是否形成待办 / 提醒时间
    ↓
Android 在上传成功后立即同步 Reminder（周期同步兜底）
    ↓
WorkManager 到点弹出提醒：[已完成] [稍后提醒] [忽略]
    ↓
状态同步回 Server
```

## 快速开始

### 环境要求

- Node.js >= 20，pnpm
- JDK 17+、Android SDK（API 35/36）
- PostgreSQL 14+（本地开发可用任意方式启动）

### 1. 准备数据库

当前环境使用独立的 PostgreSQL `unmiss` 数据库。`DATABASE_URL` 必须明确指向该库；迁移不会操作同实例中的其他数据库。

### 2. 配置并启动 Server

```bash
cd unmiss-server
pnpm install
cp .env.example .env        # 配置 DATABASE_URL / JWT_SECRET / AI_MODEL / AI_BASE_URL
# DEEPSEEK_API_KEY 只通过进程或系统环境变量提供，不写入仓库
pnpm db:migrate             # 执行 Drizzle 迁移
pnpm build
pnpm start                  # API 监听 http://localhost:3000
pnpm worker                 # 另开终端：AI 失败重试与原文清理
```

健康检查：

```bash
curl http://localhost:3000/api/v1/health
# {"status":"ok"}
```

### 3. 运行 Android App

用 Android Studio 打开 `unmiss-android/`，或命令行构建：

```bash
cd unmiss-android
./gradlew assembleRelease   # 需先配置 UNMISS_KEYSTORE_PASSWORD / UNMISS_KEY_PASSWORD
```

安装到设备/模拟器后：

1. 在设置页确认 **Server 地址**（模拟器默认 `http://10.0.2.2:3000/api/v1`；真机改为电脑局域网 IP 或服务器地址）
2. 首页引导授予 **通知使用权**（Notification Access）
3. 在 **Allowlist 页** 勾选允许采集的 App（如微信、短信、邮箱）

之后这些 App 的通知会被自动上传，等待 Agent 分析生成提醒。

## REST API 一览

前缀 `/api/v1`，除注册和健康检查外均需 `Authorization: Bearer <JWT>`。

| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| GET | `/health` | 健康检查 | ✅ 已实现 |
| POST | `/devices/register` | 设备注册，返回 JWT | ✅ 已实现 |
| POST | `/devices/push-token` | 更新设备推送 Token | ✅ 服务端已实现 |
| DELETE | `/devices/me/data` | 删除当前用户全部服务端数据 | ✅ 已实现 |
| POST | `/notifications` | 上传通知（幂等） | ✅ 已实现 |
| GET | `/reminders/pending` | 待处理提醒列表 | ✅ 已实现 |
| POST | `/reminders/:id/done` | 标记完成 | ✅ 已实现 |
| POST | `/reminders/:id/snooze` | 稍后提醒 | ✅ 已实现 |
| POST | `/reminders/:id/ignore` | 忽略 | ✅ 已实现 |

## 开发进度

- [x] **Phase 0** — 双端工程脚手架、数据库 Schema 与迁移、健康检查
- [x] **Phase 1** — Android 通知监听、权限引导、Allowlist、Room 本地队列
- [x] **Phase 2** — 设备注册（JWT）、通知上传、幂等去重、WorkManager 重试
- [x] **Phase 3** — DeepSeek V4 Flash 结构化分析、时区解析、并发领取与后台重试
- [x] **Phase 4** — Reminder 生命周期 API（pending / done / snooze / ignore + events）
- [x] **Phase 5** — Android 上传后立即同步、本地到点调度，15 分钟周期同步兜底
- [ ] **Phase 6** — FCM 服务端主动推送（MVP 不依赖，后续增强）
- [x] **Phase 7** — 通知栏快捷操作（Done / Snooze / Ignore）全链路同步
- [x] Docker Compose 使用外部 `DATABASE_URL`，API / Worker 路径已修正

## 安全与隐私

通知属于高度敏感数据，项目遵循以下原则：

- 默认不上传任何通知，仅采集用户明确勾选的 Allowlist 应用
- 通知原文仅保留 7~30 天（规划），Reminder / Event 长期保存
- Worker 默认自动清理 14 天前通知原文（可通过 `NOTIFICATION_RETENTION_DAYS` 调整）
- 不在日志中输出通知正文
- 全部业务接口需要设备 JWT 认证
- API 默认限制为每个设备/IP 每分钟 120 次请求，请求体限制为 64 KB
- 所有 Secret 通过 `.env` 管理，禁止提交到 Git

详细设计见 [docs/Unmiss_PLAN.md](docs/Unmiss_PLAN.md) 第 30~32 节。
