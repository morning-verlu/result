# Sync 应用模块详解

> 包名：`cn.verlu.sync` · 模块路径：`app/` · 平台：Android（API 26+）

---

## 1. 模块定位

**Sync** 是平台的"设备健康看板"应用。它持续采集当前手机的**电量**、**机身温度**、**屏幕使用时长**，并通过 Supabase 实时同步到云端，同时集成**天气查询**（基于实时定位）、**账号管理**与**扫码授权登录**（供 Cloud 桌面端或其它应用使用）。

---

## 2. 核心功能

| 功能 | 入口页面 | 说明 |
|------|---------|------|
| 电量监控 | `Home → 电量 Tab` | 显示当前电量百分比、设备型号，上报至 `battery_levels` |
| 屏幕时长 | `Home → 屏幕时长 Tab` | 汇总今日 / 近 7 天使用统计，上报至 `screen_time_reports` |
| 温度监控 | `Home → 温度 Tab` | 读取设备温度传感器，上报至 `temperature_levels` |
| 天气查询 | `Home → 天气 Tab` | 基于 GPS 定位查询当前天气与 3 日预报 |
| 账号管理 | `Profile` | 修改头像/昵称、修改密码、退出登录 |
| 扫码授权 | `QrScan` / 深链 | 扫码批准其他端（Cloud 桌面）的登录请求 |
| 应用更新 | 启动时自动 | 从 `app_releases` 检查新版本 |

---

## 3. 页面结构与路由

```
SyncNavApp
│
├── Splash（isInitializing=true 期间）
├── Auth 子流（未登录）
│   ├── AuthRoute（邮箱输入）
│   ├── AuthEmailRoute（注册/登录）
│   └── AuthPasswordRoute（密码输入）
│
└── Home（已登录）
    ├── HomeScreen
    │   ├── Tab 0：电量
    │   ├── Tab 1：温度
    │   ├── Tab 2：屏幕时长
    │   └── Tab 3：天气
    ├── ProfileScreen
    ├── QrScanScreen（扫码相机）
    └── SSOAuthorize(sessionId, returnPackage)
```

**深链**：`syncapp://login`（PKCE 回调）、`syncapp://authorize_sso?session_id=...`（扫码授权触发）

---

## 4. 数据架构

### 4.1 本地数据库（Room）

```
AppDatabase
├── BatteryLevelDao       → battery_levels
├── TemperatureLevelDao   → temperature_levels
├── ScreenTimeReportDao   → screen_time_reports
└── WeatherSnapshotDao    → weather_snapshots（本地缓存）
```

### 4.2 远程数据表

| 表名 | 主键 | 作用 |
|------|------|------|
| `battery_levels` | `user_id`（text） | 最新电量快照，upsert 更新 |
| `temperature_levels` | `user_id`（uuid） | 最新温度快照 |
| `screen_time_reports` | `(user_id, period)` | 今日/近7日屏幕时长 |
| `weather_snapshots` | `user_id`（text） | 天气缓存，避免频繁调 API |

### 4.3 天气数据流

```
设备 GPS 定位
      │
      ▼
QWeatherApiClient
      │  GET /functions/v1/weather-proxy?lat=xx&lon=xx
      ▼
Edge Function: weather-proxy
      │  EdDSA JWT 签名请求
      ▼
和风天气 API（v7/weather/now + 3d）
      │
      ▼
WeatherRepositoryImpl
      │  写入 Room + 同步到 weather_snapshots
      ▼
WeatherViewModel → UI
```

---

## 5. 关键组件说明

### 5.1 认证会话（AuthSessionViewModel）

- 订阅 `supabase.auth.sessionStatus`
- 冷启动防抖 500ms（等待 PKCE 会话恢复）
- `isInitializing=false` 后稳定输出 `isAuthenticated`

### 5.2 扫码授权（SSO）

用户在 Cloud 桌面或其它端生成二维码后，用 Sync 扫码触发以下流程：

```
1. CameraX 识别二维码中的 session_id
2. 深链 syncapp://authorize_sso?session_id=xxx 触发
3. SSOAuthorizeViewModel 调用 approve-login Edge Function
4. Edge Function 验证身份 → 更新 qr_login_sessions.status = approved
5. 对方端通过 Realtime 收到状态变更 → 完成登录
```

### 5.3 屏幕时长采集

- 通过系统 `UsageStatsManager` 获取 App 使用统计
- 需要用户授予**使用情况访问权限**（Settings → 应用使用情况）
- 按 `today` / `last_7_days` 两个 period 维度上报

### 5.4 Realtime 订阅

各数据 Repository 订阅对应表的变更，采用 Realtime + 指数退避重试策略（不轮询），详见[整体架构 §4](./02-architecture.md)。

---

## 6. 权限要求

| Android 权限 | 用途 |
|-------------|------|
| `ACCESS_FINE_LOCATION` | GPS 定位（天气） |
| `PACKAGE_USAGE_STATS` | 屏幕时长采集 |
| `CAMERA` | 扫码功能 |
| `INTERNET` | 网络请求 |
| `FOREGROUND_SERVICE` | 后台数据上报（如有） |

---

## 7. 配置文件

```kotlin
// app/src/main/java/cn/verlu/sync/data/remote/SupabaseConfig.kt
object SupabaseConfig {
    const val URL = "https://jlzfvxxwzcpvtzdemcpm.supabase.co"
    const val ANON_KEY = "..."
}
```

---

## 8. 模块依赖

```
app (Sync)
├── Supabase：Auth + Postgrest + Functions + Realtime
├── Room：AppDatabase（4 个实体）
├── Hilt：AppModule（提供 SupabaseClient / DB / Repositories）
├── Navigation3：SyncNavApp
├── CameraX + ML Kit：扫码
├── Play Services Location：定位
└── （逻辑独立，不依赖其它 Android 模块）
```

---

*下一章：[Doctor 模块详解](./04-doctor.md)*
