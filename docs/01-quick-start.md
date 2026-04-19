# 快速开始指南

> 本章介绍如何搭建本地开发环境并运行各个子项目。

---

## 1. 环境要求

### 1.1 通用

| 工具 | 最低版本 | 说明 |
|------|----------|------|
| Git | 2.40+ | 代码版本管理 |
| JDK | 17 | Gradle 构建 & Android |
| Android Studio | Meerkat（2024.3.1+）| Android 开发 |
| Node.js | 18+ | Admin Web |
| npm | 9+ | Admin Web 包管理 |

### 1.2 Android 应用（app / talk / doctor / cnchess / music）

- Android SDK Platform：**API 35**
- Build-Tools：**35.x**
- 模拟器或真机：Android **API 26+**（cnchess 要求 **API 33+**）

### 1.3 Cloud（KMP）

- JDK 17（Desktop 端运行）
- IntelliJ IDEA 或 Android Studio（KMP 支持插件）
- 若打包 Desktop 端：对应平台工具链（Windows MSI / macOS dmg / Linux deb）

### 1.4 Supabase

- Supabase CLI（可选，用于本地迁移与 Edge Function 开发）
- 或直接使用已部署的 Supabase 云端项目

---

## 2. 克隆仓库

```bash
git clone <仓库地址>
cd sync
```

---

## 3. Supabase 配置

所有应用共用同一 Supabase 项目，配置已写入各模块 `SupabaseConfig.kt`（或 `.env`），**无需** 重复配置即可运行。

如需指向自己的 Supabase 项目，修改以下文件中的 `URL` 与 `ANON_KEY`：

| 模块 | 配置文件 |
|------|---------|
| Sync (`app`) | `app/src/main/java/cn/verlu/sync/data/remote/SupabaseConfig.kt` |
| Talk | `talk/src/main/java/cn/verlu/talk/data/remote/SupabaseConfig.kt` |
| Doctor | `doctor/src/main/java/cn/verlu/doctor/data/remote/SupabaseConfig.kt` |
| CnChess | `cnchess/src/main/java/cn/verlu/cnchess/data/remote/SupabaseConfig.kt` |
| Music | `music/src/main/java/cn/verlu/music/data/remote/SupabaseConfig.kt` |
| Cloud | `cloud/composeApp/src/commonMain/.../data/remote/SupabaseConfig.kt` |
| Admin Web | `admin-web/.env`（复制 `.env.example` 后填写） |

### 3.1 Admin Web 环境变量配置

```bash
cd admin-web
cp .env.example .env
# 编辑 .env，填入 VITE_SUPABASE_URL 和 VITE_SUPABASE_ANON_KEY
```

### 3.2 运行数据库迁移（可选，自建 Supabase）

```bash
# 安装 Supabase CLI
npm install -g supabase

# 链接项目
supabase link --project-ref <你的项目ID>

# 执行所有迁移
supabase db push
```

### 3.3 部署 Edge Functions（可选，自建 Supabase）

```bash
# 部署全部 Functions
supabase functions deploy approve-login
supabase functions deploy weather-proxy
supabase functions deploy herb-proxy
supabase functions deploy cloud-files

# 设置 Secrets（以 weather-proxy 为例）
supabase secrets set QWEATHER_PROJECT_ID=xxx
supabase secrets set QWEATHER_KID=xxx
supabase secrets set QWEATHER_PRIVATE_KEY_B64=xxx
supabase secrets set QWEATHER_API_HOST=xxx

# herb-proxy
supabase secrets set HERB_API_KEY=xxx
supabase secrets set HERB_API_BASE=https://api.zyapi.com

# cloud-files
supabase secrets set S3_ENDPOINT=xxx
supabase secrets set S3_BUCKET=xxx
supabase secrets set S3_REGION=xxx
supabase secrets set S3_ACCESS_KEY=xxx
supabase secrets set S3_SECRET_KEY=xxx
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=xxx
```

---

## 4. Android 应用运行

### 4.1 使用 Android Studio

1. 打开 Android Studio → **Open** → 选择 `sync/`（根目录）
2. 等待 Gradle Sync 完成（首次约 2–5 分钟）
3. 在顶部模块下拉选择目标模块（`:app`、`:talk`、`:doctor`、`:cnchess`、`:music`）
4. 选择设备/模拟器 → 点击 **Run**

### 4.2 命令行构建

```bash
# Debug APK（以 talk 为例）
./gradlew :talk:assembleDebug

# Release APK（需配置签名）
./gradlew :talk:assembleRelease

# 所有模块 Debug APK
./gradlew assembleDebug

# 单元测试
./gradlew :cnchess:testDebugUnitTest
```

构建产物位于各模块 `build/outputs/apk/` 目录。

---

## 5. Cloud（KMP）运行

`cloud/` 是**独立的 Gradle 工程**，需单独打开。

```bash
cd cloud

# Android
./gradlew :composeApp:assembleDebug

# Desktop JVM 运行
./gradlew :composeApp:run

# Desktop 打包（Windows）
./gradlew :composeApp:packageMsi

# Desktop 打包（macOS）
./gradlew :composeApp:packageDmg
```

---

## 6. Admin Web 运行

```bash
cd admin-web

# 安装依赖
npm install

# 开发服务器（默认 http://localhost:5173）
npm run dev

# 生产构建
npm run build

# 预览生产构建
npm run preview
```

---

## 7. 常见问题

### Q：Gradle Sync 失败

- 确认 JDK 版本为 17（`java -version`）
- 检查 `gradle/wrapper/gradle-wrapper.properties` 中 Gradle 版本是否可下载
- 尝试：`./gradlew --stop && ./gradlew :app:assembleDebug`

### Q：应用启动后直接显示认证页

- 正常现象：未登录时所有应用都会跳转到邮箱登录页
- 使用项目提供的测试账号或在 Supabase Dashboard 注册新用户

### Q：天气功能显示"获取失败"

- 需要 Edge Function `weather-proxy` 已部署并配置了和风天气密钥
- 检查设备是否授予定位权限

### Q：CnChess 无法匹配到对手

- 确保双方账号已互为好友（在 Talk 中添加）
- 确认两个账号均在 CnChess 主页（在线状态）

### Q：Cloud Desktop 二维码登录无响应

- 确认手机端 Talk 已登录
- 检查 Edge Function `approve-login` 是否正常运行
- 查看 Supabase Dashboard → Edge Functions → 日志

---

*下一章：[整体架构设计](./02-architecture.md)*
