# Cloud 应用模块详解

> 包名：`cn.verlu.cloud` · 模块路径：`cloud/` · 平台：Android + Desktop JVM（Kotlin Multiplatform）

---

## 1. 模块定位

**Cloud** 是平台唯一的**跨端**应用，同时支持 Android 手机和桌面端（Windows/macOS/Linux）。其核心功能是**个人网盘**：文件浏览、上传、下载、分享，对象存储通过 Edge Function **`cloud-files`** 对接 S3 兼容存储（阿里云 OSS / Cloudflare R2 等）。

桌面端还支持**二维码登录**，需手机端 Talk 扫码批准，完成跨端身份传递。

> **注意**：`cloud/` 是独立的 Gradle 工程（Kotlin Multiplatform），需单独在 IDE 中打开，不属于根仓库的 Android 多模块项目。

---

## 2. 核心功能

| 功能 | 平台 | 说明 |
|------|------|------|
| 文件浏览 | Android + Desktop | 按用户隔离的目录树，前缀 `owners/{uid}/` |
| 文件上传 | Android + Desktop | 预签名上传，大文件分片，小文件服务端 proxy |
| 文件下载 | Android + Desktop | 预签名下载 URL，支持断点续传（通过传输队列） |
| 文件删除 | Android + Desktop | 服务端 `cloud-files` delete action |
| 文件移动/重命名 | Android + Desktop | 服务端 `cloud-files` move action |
| 传输队列管理 | Android + Desktop | SQLDelight 持久化传输任务状态 |
| 邮箱登录 | Android + Desktop | 与其它应用统一的 Supabase Auth |
| 二维码登录 | Desktop 专有 | 桌面展示 QR，手机 Talk 扫码批准 |
| 深链协议 | Desktop 专有 | `verlucloud://` 协议处理登录回调 |
| 应用更新 | Android + Desktop | `app_releases` 检查，Android 下载 APK，桌面下载安装包 |

---

## 3. 项目结构（KMP）

```
cloud/
├── composeApp/
│   ├── src/
│   │   ├── commonMain/         ← 跨端共享代码（UI + 业务逻辑）
│   │   ├── androidMain/        ← Android 平台特化
│   │   └── jvmMain/            ← Desktop JVM 平台特化
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml     ← Cloud 独立版本目录
└── settings.gradle.kts
```

### 3.1 共享层（commonMain）

绝大多数 UI 和业务逻辑写在 `commonMain`，平台差异通过 `expect/actual` 机制隔离：

| 类型 | 共享实现 | 平台特化 |
|------|---------|---------|
| UI | Compose Multiplatform | 底层渲染由平台处理 |
| 导航 | Voyager | — |
| DI | Koin | — |
| 数据库 | SQLDelight（CloudDatabase） | SQLite 驱动由平台提供 |
| 存储适配 | CloudEdgeFunctionAdapter | S3 操作通用 |
| 登录协议 | Auth 流程 | 桌面额外支持 QR + 深链 |

---

## 4. 页面结构与导航（Voyager）

```
CloudAppRoot（App.kt）
      │
      ├── SessionLoadingScreen（会话加载中）
      │
      ├── AuthScreen（未登录）
      │   ├── Landing（欢迎页，桌面展示 QR）
      │   ├── EmailStep（输入邮箱）
      │   └── PasswordStep（输入密码）
      │
      └── ExplorerScreen（已登录，主界面）
          ├── 文件列表
          ├── 上传入口
          ├── 传输队列管理
          └── 个人信息 / 退出
```

---

## 5. 存储架构

### 5.1 用户文件隔离

所有文件对象的 S3 key 以 `owners/{userId}/` 为前缀，RLS 或 Edge Function 层均强制此约束，用户无法访问他人文件。

### 5.2 上传流程

```
用户选择文件
      │
CloudEdgeFunctionAdapter.upload(fileInfo)
      │
      ├── 小文件（< 阈值）：POST /cloud-files?action=upload_proxy
      │       → Edge Function 接收后直接 put 到 S3
      │
      └── 大文件：多步分片上传
              │  POST /cloud-files?action=create_multipart
              │  → 获取 uploadId
              ├── 分片循环：POST /cloud-files?action=upload_part
              └── 完成：POST /cloud-files?action=complete_multipart
      │
SqlDelightTransferRepository
      │  记录传输任务状态（pending/uploading/done/failed）
      ▼
传输完成 → 刷新文件列表
```

### 5.3 下载流程

```
用户点击下载
      │
POST /cloud-files?action=presign_download
      │  Edge Function 生成预签名 URL（时效性）
      ▼
客户端使用预签名 URL 直接从 S3 下载
      │
SqlDelightTransferRepository 记录进度
      ▼
下载完成 → 系统通知 / 本地文件可用
```

---

## 6. Edge Function：cloud-files

**路径**：`supabase/functions/cloud-files/index.ts`

所有 S3 操作均通过此函数代理，确保：
- AWS 签名/密钥不下发客户端
- 所有操作必须携带有效 Supabase JWT
- 文件路径强制 `owners/{uid}/` 前缀

| action | 说明 |
|--------|------|
| `list` | 列出指定前缀下的文件和文件夹 |
| `presign_upload` | 生成预签名上传 URL |
| `presign_download` | 生成预签名下载 URL |
| `create_multipart` | 初始化分片上传 |
| `upload_part` | 上传单个分片 |
| `complete_multipart` | 完成分片上传 |
| `upload_proxy` | 小文件服务端代理上传 |
| `delete` | 删除文件/文件夹 |
| `move` | 移动/重命名（服务端 copy + delete） |

**配置的 Secrets**：

| Key | 说明 |
|-----|------|
| `S3_ENDPOINT` | S3 兼容存储端点（如 oss-cn-hangzhou.aliyuncs.com） |
| `S3_BUCKET` | Bucket 名称 |
| `S3_REGION` | 地域 |
| `S3_ACCESS_KEY` | 访问密钥 ID |
| `S3_SECRET_KEY` | 访问密钥 Secret |
| `SUPABASE_SERVICE_ROLE_KEY` | 验证用户 JWT 所需 |

---

## 7. 桌面端二维码登录

```
Cloud Desktop 启动 / 未登录
      │
AuthScreen Landing 展示 QR（使用 qrose 库）
      │  QR 内容：sessionId（已写入 qr_login_sessions）
      │  订阅 qr_login_sessions 对应行的 Realtime 变更
      ▼
用户用 Talk 手机扫码
      │  approve-login Edge Function 被调用
      │  qr_login_sessions.status = 'approved'
      │  qr_login_sessions.login_token = <一次性 token>
      ▼
Cloud Desktop Realtime 收到 UPDATE 事件
      │  取出 login_token
      ▼
SupabaseAuthRepository.exchangeToken(login_token)
      │  完成会话建立
      ▼
导航到 ExplorerScreen（已登录）
```

---

## 8. 深链协议（桌面端）

桌面端注册 `verlucloud://` 自定义协议：

| 深链 | 触发场景 |
|------|---------|
| `verlucloud://login#access_token=...` | 邮件链接登录回调 |
| `verlucloud://reset-password#...` | 密码重置回调 |

通过 `DesktopProtocolRegistrar` 在操作系统层注册协议处理器。

---

## 9. 应用更新

- **Android**：`CloudAndroidUpdateGate` → 查询 `app_releases`（`package_name = 'cn.verlu.cloud'`）
- **Desktop**：`CloudDesktopUpdateGate` → 查询（`package_name = 'cn.verlu.cloud.desktop'`），按平台提供 MSI/DMG/DEB 下载链接

---

## 10. 配置文件

```kotlin
// cloud/composeApp/src/commonMain/.../data/remote/SupabaseConfig.kt
object SupabaseConfig {
    const val URL = "https://jlzfvxxwzcpvtzdemcpm.supabase.co"
    const val ANON_KEY = "..."
    const val STORAGE_BUCKET = "cloud-user-files"
    const val REDIRECT_URI = "verlucloud://login"
    const val DESKTOP_QR_SESSION_EXPIRY_SECONDS = 300
}
```

---

## 11. 构建命令

```bash
cd cloud

# Android Debug APK
./gradlew :composeApp:assembleDebug

# Desktop 运行（开发用）
./gradlew :composeApp:run

# Desktop Windows 打包
./gradlew :composeApp:packageMsi

# Desktop macOS 打包
./gradlew :composeApp:packageDmg

# Desktop Linux 打包
./gradlew :composeApp:packageDeb
```

---

## 12. 模块依赖

```
cloud (KMP)
├── Supabase KMP SDK 3.1.4：Auth + Postgrest + Realtime + Storage + Functions
├── SQLDelight：跨端传输队列持久化（CloudDatabase）
├── Voyager：KMP 导航
├── Koin：跨端依赖注入
├── qrose：二维码生成（桌面 QR 登录）
├── Compose Multiplatform 1.10.3：跨端 UI
└── （通过 qr_login_sessions + approve-login 与 Talk 协作完成扫码登录）
```

---

*下一章：[Admin Web 模块详解](./08-admin-web.md)*
