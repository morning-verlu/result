# Talk 应用模块详解

> 包名：`cn.verlu.talk` · 模块路径：`talk/` · 平台：Android（API 26+）

---

## 1. 模块定位

**Talk** 是平台的**即时通讯**应用，支持用户间单聊、好友管理（邮箱搜索/二维码）、消息实时推送，以及跨端**扫码授权登录**（手机批准桌面/其他设备的登录请求）。

Talk 是平台中功能最复杂的模块，与 Cloud 桌面端共享扫码登录基础设施，与 CnChess 共享好友关系表。

---

## 2. 核心功能

| 功能 | 入口 | 说明 |
|------|------|------|
| 会话列表 | `Home → 会话 Tab` | 显示所有聊天会话，含最新消息预览和未读状态 |
| 单聊 | `ChatRoom(roomId)` | 文字消息发送/接收，支持消息撤回（软删除） |
| 好友列表 | `Home → 联系人 Tab` | 显示已接受好友，按首字母分组 |
| 新朋友 | `联系人 → 新的朋友 Tab` | 显示待处理的好友申请，支持同意/拒绝 |
| 添加好友 | 弹窗（邮箱搜索） | 输入邮箱搜索用户并发送申请 |
| 扫码加好友 | `QrScanFriend` | 扫描对方二维码快速添加 |
| 个人资料 | `Profile` | 展示本人二维码（供对方扫码），修改昵称/头像 |
| 扫码授权登录 | `QrScan`（通用扫码） | 批准 Cloud 桌面端或其它设备的登录请求 |
| 应用更新 | 启动时自动 | 从 `app_releases` 检查新版本 |

---

## 3. 页面结构与路由

```
TalkNavApp
│
├── Splash（isInitializing=true 期间）
├── Auth 子流（未登录）
│   ├── AuthRoute（邮箱入口）
│   │   └── Sync 扫码授权弹窗（SyncAuthorizeLauncher）
│   ├── AuthEmailRoute（邮箱/密码）
│   └── AuthPasswordRoute（密码）
│
└── Home（已登录）
    ├── HomeScreen（含底部导航）
    │   ├── 会话列表（ConversationListScreen）
    │   └── 联系人（ContactsScreen）
    │       ├── Tab 0：好友列表
    │       └── Tab 1：新的朋友（待处理申请）
    ├── ChatRoomScreen(roomId)
    ├── ProfileScreen（含二维码弹窗 ProfileQrDialog）
    └── QrScanFriendScreen（扫码加好友）
```

---

## 4. 数据架构

### 4.1 本地数据库（Room）

```
TalkDatabase
├── MessageDao         → 消息缓存（message_id, room_id, content, ...）
├── ConversationDao    → 会话列表缓存（room_id, peer_info, last_message, ...）
└── FriendshipDao      → 好友关系缓存（accepted + pending）
```

### 4.2 远程数据表

| 表名 | 作用 |
|------|------|
| `profiles` | 用户信息（头像、昵称、邮箱） |
| `friendships` | 好友关系（pending/accepted/blocked） |
| `rooms` | 聊天房间 |
| `room_members` | 房间成员（每个好友对生成一个 room） |
| `messages` | 消息记录（含软删除 `deleted_at`） |
| `message_reads` | 已读状态 |
| `qr_login_sessions` | 扫码登录会话 |

### 4.3 Realtime 订阅

| 订阅目标 | 触发动作 | 处理逻辑 |
|---------|---------|---------|
| `friendships`（INSERT/UPDATE/DELETE） | 好友状态变化 | `refreshFriends()` → Room upsert |
| `messages`（INSERT） | 收到新消息 | `refreshRoomMessages()` → Room upsert |
| `rooms`（UPDATE） | 会话最新消息变化 | `refreshConversations()` → Room upsert |
| `qr_login_sessions`（UPDATE） | 扫码会话状态 | `AuthFormViewModel` 收到批准信号 |

---

## 5. 关键流程详解

### 5.1 发送消息

```
用户输入 → ChatRoomViewModel.sendMessage(content)
      │
      ├── 乐观 UI：立即在本地消息列表末尾追加（待发送状态）
      ▼
supabase.postgrest["messages"].insert(...)
      │  RLS 校验：sender_id = auth.uid()，room_id 需在已接受好友关系中
      ▼
插入成功 → Realtime 推送给对方 → 对方 Repository 刷新 → 对方 UI 更新
```

### 5.2 消息撤回（软删除）

```
长按消息 → 撤回选项 → 更新 deleted_at = now()
      │
      ▼
Realtime UPDATE 推送 → 双方 UI 显示"此消息已撤回"占位文字
```

### 5.3 好友申请流程

```
发起方：
  输入邮箱 → searchUser(email) → 找到用户
      │
      ▼
  sendFriendRequest(addresseeId)
      │  insert into friendships(requester_id, addressee_id, status='pending')
      ▼
  数据写入数据库

接收方（当 Realtime 正常时，秒级感知；断线时重连后自动恢复）：
  friendship INSERT 事件 → refreshFriends() → Room upsert
      │
      ▼
  ContactsViewModel.observePendingRequests(userId) → 更新 "新的朋友(1)" 徽标
      │
      ▼
  用户点击"同意" → acceptFriendRequest(friendshipId)
      │  update friendships set status='accepted'
      │  触发器 handle_friendship_accepted：创建 room + room_members，设置 room_id
      ▼
  双方 Realtime UPDATE 感知 → refreshFriends() → 好友出现在列表
```

**注意**：`prevent_duplicate_friend_request` 触发器会阻止重复申请，并在反向申请存在时抛 `reverse_request_exists` 异常（UI 会提示"对方已向你发送了好友申请"）。

### 5.4 扫码加好友

```
ProfileScreen → 展示本人 QR（内含 userId）
      │
对方扫描 → QrScanFriendScreen
      │  解析 QR 中的 userId
      ▼
QrScanFriendViewModel.sendFriendRequest(userId)
      │  走同 5.3 的申请流程
      ▼
UI 提示发送成功
```

### 5.5 扫码授权登录（为 Cloud 桌面端服务）

```
Cloud 桌面展示二维码（含 sessionId）
      │
Talk 用户扫码 → 解析 sessionId
      │
AuthFormViewModel.approveLogin(sessionId)
      │  调用 approve-login Edge Function
      ▼
Edge Function 验证 JWT → 更新 qr_login_sessions
      │
Cloud 桌面 Realtime 订阅 → 收到 login_token → 完成登录
```

---

## 6. 好友关系订阅（防断线重试）

`FriendRepositoryImpl` 中实现了**指数退避 Realtime 重连**策略：

```kotlin
// 不轮询，只重连 Realtime 频道
private fun startRealtimeRetryLoop() {
    // 失败后：1s → 2s → 4s → 8s → 16s → 32s 重试
}
```

并已通过 `alter publication supabase_realtime add table public.friendships` 将该表纳入 Realtime 发布。

---

## 7. 安全设计要点

- **消息发送 RLS**：发送者必须和接收者处于"已接受好友关系"的同一 room 中
- **消息查看 RLS**：用户只能看到自己所在 room 的消息
- **好友申请 RLS**：只能以自己为 `requester_id` 发起申请
- **房间创建**：仅通过服务端触发器（`SECURITY DEFINER`）创建，不允许客户端直接 insert

---

## 8. 权限要求

| Android 权限 | 用途 |
|-------------|------|
| `INTERNET` | 网络请求 |
| `CAMERA` | 扫码（加好友 / 授权登录） |

---

## 9. 配置文件

```kotlin
// talk/src/main/java/cn/verlu/talk/data/remote/SupabaseConfig.kt
object SupabaseConfig {
    const val URL = "https://jlzfvxxwzcpvtzdemcpm.supabase.co"
    const val ANON_KEY = "..."
    const val STORAGE_BUCKET = "cloud-user-files"
}
```

---

## 10. 模块依赖

```
talk
├── Supabase：Auth + Postgrest + Realtime + Storage（附件）+ Functions
├── Room：TalkDatabase（消息/会话/好友）
├── Hilt：AppModule
├── Navigation3：TalkNavApp
├── CameraX + ML Kit + ZXing：扫码
├── Coil3：头像图片加载
└── （逻辑独立，通过 Supabase 共享数据与 Cloud 协作）
```

---

*下一章：[CnChess 模块详解](./06-cnchess.md)*
