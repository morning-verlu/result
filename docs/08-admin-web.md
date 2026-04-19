# Admin Web 模块详解

> 路径：`admin-web/` · 平台：Web 浏览器（SPA）· 技术栈：React 19 + Vite 8 + TypeScript 6

---

## 1. 模块定位

**Admin Web** 是平台的**运营管理后台**，专供管理员使用。核心职责是管理所有应用（Android APK + Desktop 安装包）的**版本发布**：上传安装包、配置版本信息（强更/灰度/更新日志）、上线/下线版本。

普通用户无需使用此后台，所有版本变更实时影响对应应用的启动时更新检查。

---

## 2. 核心功能

| 功能 | 说明 |
|------|------|
| 管理员登录 | 邮箱密码 + Google/GitHub OAuth |
| 查看版本列表 | 按应用分类展示所有版本，含状态、创建时间 |
| 发布新版本 | 上传安装包（最大 200MB）、填写版本信息 |
| 启用/停用版本 | 切换 `enabled` 状态，立即影响客户端检查结果 |
| 编辑版本信息 | 修改版本名、更新日志、灰度配置等 |
| 删除版本 | 同时删除 Storage 中的安装包对象（best-effort） |
| 权限提示 | 未被加入 `app_release_admins` 时，展示引导说明 |

---

## 3. 支持的应用包名

Admin Web 内置以下包名供选择：

| 包名 | 应用 | 平台 |
|------|------|------|
| `cn.verlu.sync` | Sync | Android |
| `cn.verlu.talk` | Talk | Android |
| `cn.verlu.music` | Music | Android |
| `cn.verlu.doctor` | Doctor | Android |
| `cn.verlu.cnchess` | CnChess | Android |
| `cn.verlu.cloud` | Cloud | Android |
| `cn.verlu.cloud.desktop` | Cloud Desktop | Windows/macOS/Linux |

支持的安装包格式：`.apk`、`.msi`、`.dmg`、`.deb`、`.exe`、`.zip`

---

## 4. 版本发布流程

### 4.1 完整发布步骤

```
1. 登录 Admin Web（管理员账号）
      │
2. 点击「发布新版本」
      │
3. 填写表单：
   ├── 包名（选择目标应用）
   ├── Version Code（整数，递增）
   ├── Version Name（如 1.2.3）
   ├── 更新日志（Changelog）
   ├── 最低支持版本（低于此版本强制更新）
   ├── 是否强制更新（force = true/false）
   ├── 是否灰度（仅特定用户可见）
   └── 上传安装包文件（最大 200MB）
      │
4. 提交 → 文件上传到 Storage bucket: app-releases
      │    路径：{packageName}/{versionCode}/{filename}
      │
5. 记录写入 app_releases 表
      │    含 download_url（Storage 公开 URL）
      │
6. 客户端下次启动时检测到新版本
```

### 4.2 版本状态管理

```
新发布 → enabled=true（默认上线）
      │
管理员可随时切换：
      ├── 停用（enabled=false）→ 客户端不再检测到此版本
      └── 重新启用（enabled=true）→ 恢复可见

自动规则：
      当一个包名新启用版本时，之前的 enabled=true 版本自动停用
      （由数据库触发器实现，见迁移文件）
```

---

## 5. 权限控制

Admin Web 依赖 **`app_release_admins`** 表实现权限控制：

```sql
-- 只有 app_release_admins 中的用户才能写入 app_releases
CREATE POLICY "admins_can_manage_releases"
ON public.app_releases
FOR ALL
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.app_release_admins
    WHERE user_id = auth.uid()
  )
)
```

**添加管理员步骤**：
1. 在 Supabase Dashboard → SQL Editor 执行：
```sql
INSERT INTO public.app_release_admins (user_id)
VALUES ('<目标用户的 auth.users id>');
```
2. 或通过 Supabase Dashboard → Table Editor → `app_release_admins` 添加记录。

> Admin Web 界面会在操作失败时提示"权限不足，请联系管理员将您加入 `app_release_admins`"。

---

## 6. 环境配置

```bash
# admin-web/.env（复制自 .env.example）
VITE_SUPABASE_URL=https://jlzfvxxwzcpvtzdemcpm.supabase.co
VITE_SUPABASE_ANON_KEY=your_anon_key_here
```

Storage 配置：
- Bucket 名称：`app-releases`
- 文件大小上限：**200MB**（由 Storage 策略限制）
- 文件访问：公开可读（客户端直接下载）

---

## 7. 部署方式

### 7.1 本地开发

```bash
cd admin-web
npm install
npm run dev
# 访问 http://localhost:5173
```

### 7.2 生产部署（推荐）

**Vercel 一键部署**（推荐，免费额度足够）：
1. 在 Vercel 导入 `admin-web/` 目录
2. 设置环境变量：`VITE_SUPABASE_URL`、`VITE_SUPABASE_ANON_KEY`
3. 构建命令：`npm run build`，输出目录：`dist`

**Nginx 静态托管**：
```bash
npm run build
# 将 dist/ 内容部署到 Web 服务器
```

### 7.3 OAuth 登录配置（可选）

若要启用 Google/GitHub OAuth，在 Supabase Dashboard → Authentication → Providers：
1. 启用 Google OAuth，配置 Client ID & Secret
2. 启用 GitHub OAuth，配置 Client ID & Secret
3. 在 Admin Web 设置的 Site URL 中添加回调 URL

---

## 8. 技术细节

### 8.1 单页应用结构

Admin Web 是简洁的**单文件 App**（`src/App.tsx`），无路由拆分：

```
App.tsx
├── 未登录状态 → 登录表单（邮箱/密码 + OAuth 按钮）
└── 已登录状态 → 版本管理界面
    ├── 版本列表（Table）
    ├── 发布新版本（Form + FileUpload）
    ├── 编辑版本（Modal）
    └── 管理员操作（启停/删除）
```

### 8.2 实时订阅

版本列表通过 **Supabase Realtime** 订阅 `app_releases` 变更，多个管理员同时操作时界面自动同步。

---

## 9. 模块依赖

```
admin-web
├── @supabase/supabase-js ^2.103：Auth + Postgrest + Storage + Realtime
├── React 19 + TypeScript 6：UI 框架
├── Vite 8：构建工具
└── （无路由库，单页应用）
```

---

*下一章：[数据库与后端服务](./09-supabase.md)*
