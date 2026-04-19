# 整体架构设计

---

## 1. 系统全景

```
┌──────────────────────────────────────────────────────────────────────┐
│                           客户端层                                    │
│                                                                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │  Sync    │ │  Talk    │ │  Doctor  │ │ CnChess  │ │  Music   │  │
│  │ Android  │ │ Android  │ │ Android  │ │ Android  │ │ Android  │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────────┘  │
│       │             │            │             │                      │
│       └─────────────┴────────────┴─────────────┘                    │
│                     │  Supabase Kotlin SDK                           │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                    Cloud（Android + Desktop JVM）               │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                    Admin Web（React SPA）                       │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────┬───────────────────────────────────┘
                                   │ HTTPS / WebSocket
┌──────────────────────────────────▼───────────────────────────────────┐
│                        Supabase 平台                                  │
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌────────────────┐  │
│  │  Auth    │  │PostgREST │  │   Realtime   │  │    Storage     │  │
│  │          │  │ REST API │  │  WebSocket   │  │  (S3 兼容)     │  │
│  └──────────┘  └──────────┘  └──────────────┘  └────────────────┘  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    PostgreSQL 数据库                          │   │
│  │  profiles · friendships · rooms · messages                   │   │
│  │  chess_games · chess_moves · chess_invites                   │   │
│  │  battery_levels · temperature_levels · screen_time_reports   │   │
│  │  weather_snapshots · qr_login_sessions · app_releases        │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    Edge Functions（Deno）                     │   │
│  │  approve-login  ·  weather-proxy  ·  herb-proxy  ·  cloud-files│  │
│  └──────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. Android 应用内架构

所有 Android 应用（Sync / Talk / Doctor / CnChess / Music）均采用统一的**分层架构**，遵循 Google 推荐的 MVVM + Repository 模式。

### 2.1 分层职责

```
┌─────────────────────────────────────────┐
│           Presentation 层               │
│                                         │
│   Screen (Composable)                   │
│       ↕ State / Event                   │
│   ViewModel (Hilt + StateFlow)          │
└─────────────────┬───────────────────────┘
                  │ 调用
┌─────────────────▼───────────────────────┐
│              Domain 层                  │
│                                         │
│   Repository Interface                  │
│   Domain Model（纯 Kotlin 数据类）       │
│   业务规则（如 RuleEngine / RepetitionJudge）│
└─────────────────┬───────────────────────┘
                  │ 实现
┌─────────────────▼───────────────────────┐
│               Data 层                   │
│                                         │
│   RepositoryImpl                        │
│       ├── Local: Room DAO + Entity      │
│       │       ↕ 映射                    │
│       └── Remote: Supabase SDK / DTO   │
│               ├── Postgrest（CRUD）     │
│               ├── Realtime（推送订阅）   │
│               ├── Auth（会话管理）      │
│               └── Functions（边缘计算） │
└─────────────────────────────────────────┘
```

### 2.2 依赖注入

使用 **Hilt**（Dagger2 封装），各模块的 `AppModule.kt` 负责：

- 提供 `SupabaseClient`（单例）
- 提供 Room Database 及各 DAO
- 绑定 Repository 接口到实现类
- 提供 `IoDispatcher`（协程调度器）

### 2.3 认证状态管理

每个应用都有 `AuthSessionViewModel`，负责：

```
Supabase Auth Flow
      │
      ▼
SessionStatus: Initializing → NotAuthenticated / Authenticated
      │
      ▼ (collectLatest + 500ms 冷启动防抖)
AuthSessionViewModel
      │
      ├── isInitializing = true  →  显示 Splash
      ├── isInitializing = false
      │       isAuthenticated = false  →  导航到 Auth
      │       isAuthenticated = true   →  导航到 Home
      └── 主动登出  →  立即导航到 Auth（不防抖）
```

**关键设计**：冷启动防抖逻辑集中在 ViewModel，Navigation 层不写 `delay` 补丁，确保"已登录用户冷启动直接进首页，不会闪一下 Auth 页"。

### 2.4 导航

使用 **Navigation3**（`androidx.navigation3`），路由定义为密封类或 `@Serializable` 数据类，所有路由集中在 `*NavApp.kt` 文件中管理。

---

## 3. 数据同步策略

### 3.1 本地优先（Room + Realtime）

适用于：Talk（消息、好友）、Sync（设备指标）

```
┌─────────────────────────────────────────────────────┐
│  UI（Compose）                                       │
│    collectAsStateWithLifecycle(Room Flow)            │
│    立即响应本地数据变更，无网络延迟                     │
└──────────────────────────┬──────────────────────────┘
                           │ 观察 Room Flow
┌──────────────────────────▼──────────────────────────┐
│  Room（本地 SQLite）                                  │
│    upsertAll() 写入 → Flow emit → UI 刷新             │
└──────────────────────────┬──────────────────────────┘
                           │ 两路写入
         ┌─────────────────┴──────────────────┐
         ▼                                    ▼
  Realtime 推送                         HTTP Refresh
  （subscribeToXxxChanges）              （refreshXxx）
  Supabase→通知→Repository→刷新→Room    主动拉取→写入Room
```

### 3.2 纯云端（CnChess 对局）

象棋对局数据不写 Room，直接使用 `StateFlow<ChessGame?>` 维护内存状态：

```
make_move_v2 RPC
      │
      ├── 乐观更新：_gameState.value = optimisticGame（立即刷新 UI）
      │
      ▼ 异步
Supabase RPC 响应
      ├── ok=true：refreshGame()（从服务端同步最终状态）
      ├── ok=false（违规/超时）：throw → UI 展示错误 + 回滚
      └── 网络异常：fallback 直接写库（兜底，不影响体验）
```

### 3.3 代理转发（Doctor 本草 / Sync 天气）

```
客户端 → Supabase Edge Function → 第三方 API
                ↑
         统一鉴权（验证 JWT）
         密钥不下发客户端
         CORS 处理
```

---

## 4. Realtime 订阅架构

```
Supabase Realtime Server（WebSocket）
         │
         │  postgres_changes
         ▼
RealtimeChannel（客户端订阅对象）
         │
         ├── INSERT → Repository.refreshXxx() → Room
         ├── UPDATE → Repository.refreshXxx() → Room
         └── DELETE → Repository.refreshXxx() → Room
```

**防断线设计**：各 Repository 中的 `subscribeToXxxChanges()` 采用：

1. 清理旧 channel，避免重复订阅
2. `keepXxxSubscription = true` 标记意图
3. 指数退避重试（1s → 2s → 4s → 8s → 16s → 32s），不轮询数据

---

## 5. 安全设计

### 5.1 行级安全（RLS）

所有业务表均启用 RLS，典型策略：

| 表 | 策略 | 规则摘要 |
|----|------|---------|
| `friendships` | see own | `auth.uid() = requester_id OR addressee_id` |
| `friendships` | send | `auth.uid() = requester_id` |
| `friendships` | accept/reject | `auth.uid() = addressee_id` |
| `messages` | insert | 必须是接受好友关系的 room 成员 |
| `chess_games` | 服务端 RPC | `SECURITY DEFINER` 函数统一校验 |
| `app_releases` | write | 仅 `app_release_admins` 成员 |

### 5.2 API 密钥隔离

| 密钥 | 位置 | 说明 |
|------|------|------|
| 和风天气 JWT | Supabase Secrets | 仅 `weather-proxy` 函数可读 |
| 本草 API Key | Supabase Secrets | 仅 `herb-proxy` 函数可读 |
| S3 凭证 | Supabase Secrets | 仅 `cloud-files` 函数可读 |
| Supabase Anon Key | 客户端代码 | 公开可读，受 RLS 约束 |
| Supabase Service Role | Edge Functions | 服务端专用，不下发客户端 |

### 5.3 扫码登录安全机制

- `qr_login_sessions` 记录 `expires_at`，过期失效
- `approve-login` 函数校验调用者 JWT 身份再审批
- 一次性 `login_token`，使用后状态变为 `approved`

---

## 6. 应用更新机制

```
Android 应用启动
      │
      ▼
AppUpdateGate（门控 Composable）
      │
      ▼
查询 app_releases（按 package_name + enabled=true 过滤）
      │
      ├── 无更新 / 灰度不命中 → 正常显示内容
      ├── 软更新（force=false）→ 可跳过弹窗
      └── 强制更新（force=true）→ 不可跳过，必须下载
                    │
                    ▼
              下载 APK / 安装包（Storage URL）
```

管理员通过 **Admin Web** 上传包并配置版本信息，全平台统一生效。

---

*下一章：[Sync 模块详解](./03-sync.md)*
