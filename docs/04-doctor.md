# Doctor 应用模块详解

> 包名：`cn.verlu.doctor` · 模块路径：`doctor/` · 平台：Android（API 26+）

---

## 1. 模块定位

**Doctor** 是面向中医爱好者的**本草/中医科普内容阅读**应用。用户可浏览本草文章、搜索药材与方剂、查看 Markdown 格式的详情页，并支持**收藏**与**离线缓存**。所有内容 API 请求均通过 Edge Function **`herb-proxy`** 转发，API 密钥不下发到客户端，保证安全性。

---

## 2. 核心功能

| 功能 | 入口页面 | 说明 |
|------|---------|------|
| 首页内容 | `Home` | 展示推荐文章列表、分类导航 |
| 搜索 | `HerbSearch` | 关键词搜索药材/方剂，结果本地缓存 |
| 文章详情 | `HerbArticle(path)` | Markdown 渲染，支持图片、代码块 |
| 收藏 | `Favorites` | 本地 Room 管理收藏条目 |
| 浏览历史 | 自动记录 | Room 缓存最近浏览记录 |
| 账号管理 | `Profile` | 同其它应用，统一 Supabase 账号 |
| 应用更新 | 启动时自动 | 从 `app_releases` 检查新版本 |

---

## 3. 页面结构与路由

```
DoctorNavApp
│
├── Splash（isInitializing=true 期间）
├── Auth 子流（未登录）
│   ├── AuthRoute
│   ├── AuthEmailRoute
│   └── AuthPasswordRoute
│
└── Home（已登录）
    ├── HerbMainShell（主框架，含底部导航）
    │   ├── Tab：首页内容
    │   ├── Tab：搜索
    │   └── Tab：收藏
    ├── HerbArticle(path)
    └── ProfileScreen
```

---

## 4. 数据架构

### 4.1 本地数据库（Room）

```
HerbDatabase
├── ArticleDao        → 文章列表缓存
├── SearchDao         → 搜索历史 & 结果缓存
├── BrowseHistoryDao  → 最近浏览记录
└── FavoriteDao       → 收藏条目
```

### 4.2 网络请求路径

Doctor 没有独立的 Supabase 业务表，所有内容均来自第三方本草 API，通过 Edge Function 代理：

```
HerbRepository（客户端）
      │
      │  GET /functions/v1/herb-proxy?p=/herb/search?q=...
      │  Header: Authorization: Bearer <用户JWT>
      ▼
Edge Function: herb-proxy（服务端）
      │  验证 JWT 合法性
      │  读取 HERB_API_KEY、HERB_API_BASE（Secrets）
      ▼
第三方本草 API（zyapi 等）
      │
      ▼
响应 JSON → 客户端解析 → 写入 Room → UI 展示
```

**设计优势**：
- API Key 仅存于 Supabase Secrets，客户端无法获取
- 请求需携带有效 JWT，防止未授权调用
- 服务端统一处理 CORS、超时、错误格式

### 4.3 离线策略

- 首次加载文章时写入 Room 缓存
- 下次访问时先展示 Room 数据，后台静默刷新
- 收藏与浏览历史仅本地存储，不同步云端

---

## 5. 关键组件说明

### 5.1 Markdown 渲染

使用 **multiplatform-markdown-renderer** 库渲染文章详情：

- 支持标题、段落、列表、代码块、表格
- 图片通过 **Coil3** 异步加载
- 中医术语、药材名称保持原有格式

### 5.2 搜索功能

```
用户输入关键词
      │
      ▼
SearchViewModel.search(query)
      │  防抖（300ms 去重）
      ▼
HerbRepository.searchHerbs(query)
      │  先查 Room 缓存
      │  无缓存或缓存过期 → 调 herb-proxy
      ▼
结果写入 Room → StateFlow → UI 更新
```

### 5.3 收藏管理

- 收藏操作直接写 Room，响应即时
- 首页/详情页均可收藏/取消收藏
- 收藏列表按时间倒序展示

---

## 6. 权限要求

| Android 权限 | 用途 |
|-------------|------|
| `INTERNET` | 网络请求 |

无需其它特殊权限。

---

## 7. Edge Function 说明（herb-proxy）

**路径**：`supabase/functions/herb-proxy/index.ts`

**功能**：
1. 解析请求头中的 `Authorization: Bearer <JWT>`
2. 调用 Supabase Auth 验证 JWT 有效性
3. 取查询参数 `p`（目标 API 路径）
4. 使用 `HERB_API_KEY` 发起 GET 请求到 `HERB_API_BASE + p`
5. 透传响应体与状态码

**配置的 Secrets**：

| Key | 说明 |
|-----|------|
| `HERB_API_KEY` | 第三方本草 API 授权 Key |
| `HERB_API_BASE` | API 基础 URL，默认 `https://api.zyapi.com` |

---

## 8. 配置文件

```kotlin
// doctor/src/main/java/cn/verlu/doctor/data/remote/SupabaseConfig.kt
object SupabaseConfig {
    const val URL = "https://jlzfvxxwzcpvtzdemcpm.supabase.co"
    const val ANON_KEY = "..."
}
```

---

## 9. 模块依赖

```
doctor
├── Supabase：Auth + Functions（herb-proxy）+ Realtime（会话恢复）
├── Room：HerbDatabase（文章/搜索/历史/收藏）
├── Hilt：AppModule
├── Navigation3：DoctorNavApp
├── multiplatform-markdown-renderer：文章详情渲染
├── Coil3：图片加载
└── （逻辑独立，不依赖其它 Android 模块）
```

---

*下一章：[Talk 模块详解](./05-talk.md)*
