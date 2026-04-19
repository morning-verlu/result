# Verlu 应用平台 — 项目总览

> 版本：1.0.0 · 更新日期：2026-04-15

---

## 1. 产品简介

Verlu 是一套以**统一账号体系**为核心的多端应用平台，涵盖 Android 移动端、桌面端（JVM）与 Web 管理后台。所有应用共享同一个 **Supabase** 后端项目，通过统一认证、公共数据表与 Edge Function 协同工作，形成完整的"个人生活数字化"生态。

---

## 2. 应用矩阵

| 应用 | 模块路径 | 包名 | 平台 | 定位 |
|------|----------|------|------|------|
| **Sync** | `app/` | `cn.verlu.sync` | Android | 设备状态监控与同步（电量、温度、屏幕时长、天气） |
| **Talk** | `talk/` | `cn.verlu.talk` | Android | 即时通讯（单聊、好友、扫码加友、扫码登录） |
| **Doctor** | `doctor/` | `cn.verlu.doctor` | Android | 中医本草科普内容阅读（文章浏览、搜索、收藏） |
| **CnChess** | `cnchess/` | `cn.verlu.cnchess` | Android | 在线中国象棋（好友对局、棋钟、判和、历史回放） |
| **Music** | `music/` | `cn.verlu.music` | Android | 本地+在线音乐播放（发现、收藏、下载、睡眠定时） |
| **Cloud** | `cloud/` | `cn.verlu.cloud` | Android & Desktop | 个人网盘（文件浏览、上传下载、跨端授权登录） |
| **Admin Web** | `admin-web/` | — | Web（浏览器） | 运营管理后台（版本发布、灰度、强更） |

---

## 3. 共享基础设施

```
┌─────────────────────────────────────────────────────────────┐
│                      Supabase 项目                          │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │  Auth    │  │Postgrest │  │ Realtime │  │ Storage  │  │
│  │ (统一账号)│  │ (REST API)│  │(推送订阅) │  │(文件存储) │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
│                                                             │
│  Edge Functions:                                            │
│  • approve-login   扫码登录批准                              │
│  • weather-proxy   和风天气代理                              │
│  • herb-proxy      本草 API 代理                            │
│  • cloud-files     S3 网盘操作                              │
└─────────────────────────────────────────────────────────────┘
         ▲            ▲           ▲           ▲
         │            │           │           │
    Sync/Talk     Doctor      CnChess       Cloud
```

### 3.1 统一认证

所有应用均使用 **Supabase Auth（PKCE 流程）**，用邮箱密码注册/登录。用户信息存储于 `auth.users`，公开展示信息存储于 `profiles` 表。

### 3.2 应用更新机制

所有 Android 应用（及 Cloud 桌面端）均接入统一的**应用更新服务**：

1. 应用启动时查询 `app_releases` 表（按包名过滤）
2. 若后端标记为**强制更新**，应用展示不可跳过的更新弹窗
3. 若标记为**灰度更新**，仅特定用户可见
4. 管理员通过 Admin Web 上传安装包并配置版本信息

### 3.3 扫码登录跨端授权

Talk（手机端）与 Cloud（桌面端）共享 **`qr_login_sessions`** 机制：

1. 桌面 Cloud 生成二维码（含 `sessionId`）
2. 手机 Talk 扫码 → 调用 `approve-login` Edge Function
3. 桌面端通过 Realtime 订阅 session 状态变更，收到 `login_token` 后完成登录

---

## 4. 技术规范

### 4.1 Android 公共技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 2.3.20 | 开发语言 |
| Android Gradle Plugin | 9.1.0 | 构建 |
| Jetpack Compose BOM | 2026.03.01 | UI 框架 |
| Material3 | 1.5.0-alpha16 | 设计系统 |
| Hilt | 2.59.2 | 依赖注入 |
| Navigation3 | 1.0.1 | 页面路由 |
| Room | 2.8.4 | 本地数据库 |
| Supabase Kotlin SDK | 3.5.0 | 后端接入 |
| Ktor Client | 3.4.2 | HTTP 客户端 |
| Coil3 | 3.4.0 | 图片加载 |
| KSP | 2.3.6 | 注解处理器 |

### 4.2 Cloud（KMP）技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Compose Multiplatform | 1.10.3 | 跨端 UI |
| Voyager | — | 导航 |
| Koin | — | 依赖注入 |
| SQLDelight | — | 跨端数据库 |
| Supabase Kotlin SDK | 3.1.4 | 后端接入 |

### 4.3 Admin Web 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| React | 19 | UI 框架 |
| Vite | 8 | 构建工具 |
| TypeScript | 6 | 开发语言 |
| supabase-js | ^2.103 | 后端接入 |

---

## 5. 仓库结构说明

```
sync/                         ← 根仓库（Gradle 多模块）
├── app/                      ← Sync Android 应用
├── talk/                     ← Talk Android 应用
├── doctor/                   ← Doctor Android 应用
├── cnchess/                  ← CnChess Android 应用
├── music/                    ← Music Android 应用
├── cloud/                    ← Cloud KMP 工程（独立 Gradle）
├── admin-web/                ← Admin 后台（React/Vite）
├── supabase/
│   ├── migrations/           ← 数据库 Schema 迁移文件
│   └── functions/            ← Edge Functions（Deno/TypeScript）
├── docs/                     ← 本使用手册
└── gradle/
    └── libs.versions.toml    ← Android 统一依赖版本目录
```

> **注意**：`cloud/` 有独立的 `settings.gradle.kts` 和版本目录，与根工程的 Android 模块相互独立，需单独用 Android Studio 或 IntelliJ 打开。

---

## 6. 模块间关联关系

```
            ┌─────────────────────────────────────────────────┐
            │              共同依赖（Supabase 项目）            │
            │                                                 │
            │  profiles  friendships  rooms  messages         │
            │  qr_login_sessions  app_releases                │
            │  battery_levels  screen_time_reports 等          │
            └──────────────────────┬──────────────────────────┘
                                   │ 统一账号
         ┌─────────┬───────────────┼───────────────┬──────────┐
         ▼         ▼               ▼               ▼          ▼
       Sync      Talk           Doctor          CnChess     Music
    设备数据    聊天/好友       本草内容         象棋对局    音乐播放
         │         │
         │         │  扫码登录授权（qr_login_sessions）
         │         └─────────────────────────────────────────►Cloud
         │                                                   网盘/桌面
         └─────────────────── 同步版本更新（app_releases）────────►
                                                           Admin Web
                                                           版本管理
```

---

*下一章：[快速开始指南](./01-quick-start.md)*
