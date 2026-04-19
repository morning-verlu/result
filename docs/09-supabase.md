# 数据库与后端服务详解

> Supabase 项目 URL：`https://jlzfvxxwzcpvtzdemcpm.supabase.co`

---

## 1. 概述

平台所有应用共用**一个 Supabase 项目**，包含：

- **PostgreSQL 数据库**：业务数据存储
- **Auth**：统一用户认证
- **PostgREST**：自动生成的 REST API
- **Realtime**：基于 PostgreSQL logical replication 的实时推送
- **Storage**：文件存储（安装包、用户文件）
- **Edge Functions**：Deno 运行时，代理第三方服务

迁移文件位于 `supabase/migrations/`，按时间戳命名，记录所有 Schema 变更历史。

---

## 2. 数据表详解

### 2.1 用户与关系

#### `profiles`（用户公开信息）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | uuid PK | 关联 `auth.users.id` |
| `display_name` | text | 显示名称，默认 `'User'` |
| `avatar_url` | text | 头像 URL |
| `email` | text | 邮箱（冗余，方便查询） |
| `online_at` | timestamptz | 最近在线时间 |
| `created_at` | timestamptz | 注册时间 |

**触发器**：`on_auth_user_created`（`AFTER INSERT ON auth.users`）→ 自动创建 `profiles` 记录，`display_name` 从 `raw_user_meta_data` 取 `full_name` / `user_name` / 邮箱前缀。

---

#### `friendships`（好友关系）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | uuid PK | 关系记录 ID |
| `requester_id` | uuid FK→auth.users | 发起方 |
| `addressee_id` | uuid FK→auth.users | 接收方 |
| `status` | text | `pending` / `accepted` / `blocked` |
| `created_at` | timestamptz | 申请时间 |
| `updated_at` | timestamptz | 状态变更时间 |
| `room_id` | uuid FK→rooms | 接受后绑定聊天室（可空） |

**唯一约束**：`(requester_id, addressee_id)`，防止重复申请。

**触发器**：
- `prevent_duplicate_friend_request`（BEFORE INSERT）：若存在 `(addressee_id→requester_id)` 的反向记录，抛 `reverse_request_exists`
- `on_friendship_accepted`（BEFORE UPDATE）：`status pending→accepted` 时，自动创建 room + room_members，设置 `room_id`；同时删除反向 pending 请求

**RLS 策略**：

| 策略名 | 操作 | 条件 |
|--------|------|------|
| see own friendships | SELECT | `uid = requester_id OR addressee_id` |
| send friend request | INSERT | `uid = requester_id` |
| accept/reject request | UPDATE | `uid = addressee_id` |
| delete friendship | DELETE | `uid = requester_id OR addressee_id` |

**Realtime**：已加入 `supabase_realtime` publication（迁移 `20260415234000`）。

---

### 2.2 聊天

#### `rooms`（聊天室）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | uuid PK | 房间 ID |

> 由 `handle_friendship_accepted` 触发器创建，无需客户端手动操作。

**RLS**：用户只能查看自己通过 `room_members` 关联的 room。

---

#### `room_members`（房间成员）

| 字段 | 类型 | 说明 |
|------|------|------|
| `room_id` | uuid FK→rooms | 房间 |
| `user_id` | uuid FK→auth.users | 成员 |

**RLS**：用户只能查看自己的成员记录。

---

#### `messages`（消息）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | uuid PK | 消息 ID |
| `room_id` | uuid FK→rooms | 所属房间 |
| `sender_id` | uuid FK→auth.users | 发送者 |
| `content` | text | 消息内容 |
| `type` | text | 消息类型（默认 `text`） |
| `created_at` | timestamptz | 发送时间 |
| `deleted_at` | timestamptz | 撤回时间（软删除，可空） |

**RLS**：
- INSERT：`sender_id = uid` 且 `room_id` 在用户已接受好友关系中
- SELECT/UPDATE：用户在该 room 的 `room_members` 中

---

#### `message_reads`（消息已读）

| 字段 | 类型 | 说明 |
|------|------|------|
| `message_id` | uuid FK→messages | 消息 |
| `user_id` | uuid FK→auth.users | 已读用户 |
| `read_at` | timestamptz | 已读时间 |

---

### 2.3 设备指标（Sync 应用）

#### `battery_levels`

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | text PK | 用户 ID（text 类型） |
| `battery_percent` | integer | 电量百分比 |
| `updated_at` | bigint | 更新时间戳（毫秒） |
| `device_model` | text | 设备型号 |
| `device_friendly_name` | text | 设备别名 |

**RLS**：authenticated 用户可读写所有行（多设备展示场景）。

---

#### `temperature_levels`

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | uuid FK→auth.users PK | 用户 ID |
| `temperature` | float | 温度值 |
| `updated_at` | timestamptz | 更新时间 |

---

#### `screen_time_reports`

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | uuid FK→auth.users | 用户 ID |
| `period` | text | `today` / `last_7_days` |
| `data` | jsonb | 各应用时长数据 |
| `updated_at` | timestamptz | 更新时间 |

**约束**：`period` 仅允许 `today` 或 `last_7_days`。复合主键 `(user_id, period)`。

---

#### `weather_snapshots`

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | text PK | 用户 ID（text） |
| `lat` / `lon` | float | 定位坐标 |
| `weather_json` | jsonb | 和风天气响应缓存 |
| `updated_at` | timestamptz | 更新时间 |

---

### 2.4 象棋（CnChess）

#### `cnchess_presence`（在线状态）

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | uuid PK FK→auth.users | 用户 |
| `status` | text | `online` / `away` / `offline` |
| `last_heartbeat` | timestamptz | 最近心跳时间 |

**RLS SELECT**：自己的记录，或已接受好友的记录可见。

---

#### `chess_games`（对局）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | uuid PK | 对局 ID |
| `red_user_id` / `black_user_id` | uuid FK | 红黑双方用户 |
| `status` | text | `active` / `finished` / `aborted` / `timeout` / `resigned` / `draw` |
| `fen` | text | 当前棋盘 FEN 字符串 |
| `turn_side` | text | `red` / `black` |
| `move_no` | bigint | 当前走子序号（乐观锁） |
| `red_time_ms` / `black_time_ms` | bigint | 剩余棋钟（毫秒） |
| `winner_user_id` | uuid | 胜者（可空） |
| `last_move_at` | timestamptz | 最近走子时间 |
| `draw_reason` | text | 和棋原因 |
| `draw_offer_by_user_id` | uuid | 提议和棋的用户（可空） |
| `draw_offer_at` | timestamptz | 提议时间 |
| `created_at` | timestamptz | 对局创建时间 |

**Realtime**：已加入 `supabase_realtime` publication。

---

#### `chess_invites`（邀请）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | uuid PK | 邀请 ID |
| `from_user_id` / `to_user_id` | uuid FK | 邀请双方 |
| `status` | text | `pending` / `accepted` / `rejected` / `expired` |
| `game_id` | uuid FK→chess_games | 接受后绑定对局（可空） |
| `created_at` | timestamptz | 邀请时间 |

**Realtime**：已加入 `supabase_realtime` publication。

---

#### `chess_moves`（走子记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | uuid PK | 走子记录 ID |
| `game_id` | uuid FK→chess_games CASCADE | 对局 |
| `move_no` | bigint | 走子序号（唯一约束 with game_id） |
| `side` | text | `red` / `black` |
| `from_row` / `from_col` / `to_row` / `to_col` | integer | 坐标 |
| `fen_before` / `fen_after` | text | 走子前后 FEN |
| `spent_ms` | bigint | 本步耗时 |
| `position_hash` | text | 局面哈希（用于重复判断） |
| `is_check` | boolean | 是否将军 |
| `is_chase` | boolean | 是否捉子 |
| `judge_tag` | text | 棋规标签（check/chase/illegal_long_check 等） |
| `created_at` | timestamptz | 走子时间 |

**Realtime**：已加入 `supabase_realtime` publication。

---

### 2.5 扫码登录

#### `qr_login_sessions`

| 字段 | 类型 | 说明 |
|------|------|------|
| `session_id` | uuid PK | 会话 ID（QR 内容） |
| `status` | text | `pending` / `approved` / `expired` |
| `login_token` | text | 一次性登录令牌（批准后生成） |
| `expires_at` | timestamptz | 过期时间 |
| `created_at` | timestamptz | 创建时间 |

**Realtime**：已加入 `supabase_realtime` publication（Talk + Cloud 共用）。

**RLS**：任何人可 INSERT 和 SELECT（二维码展示与扫描均需）。

---

### 2.6 应用版本

#### `app_release_admins`（管理员）

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | uuid PK FK→auth.users | 管理员用户 |

---

#### `app_releases`（版本发布记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | uuid PK | 版本记录 ID |
| `package_name` | text | 应用包名 |
| `version_code` | integer | 版本号（整数，递增） |
| `version_name` | text | 版本名（如 1.2.3） |
| `download_url` | text | 安装包下载 URL |
| `file_size` | bigint | 文件大小（字节） |
| `changelog` | text | 更新日志 |
| `min_supported_version` | integer | 低于此版本强制更新 |
| `force_update` | boolean | 是否强制更新 |
| `is_rollout` | boolean | 是否灰度 |
| `enabled` | boolean | 是否上线 |
| `created_by` | uuid FK→auth.users | 发布者 |
| `created_at` | timestamptz | 发布时间 |

**RLS**：读取公开；写入需在 `app_release_admins` 中。

---

## 3. 关键 RPC 函数

### 3.1 `make_move_v2`（象棋走子）

```sql
make_move_v2(
  p_game_id, p_move_no, p_side,
  p_from_row, p_from_col, p_to_row, p_to_col,
  p_fen_before, p_fen_after,
  p_spent_ms, p_position_hash,
  p_is_check, p_is_chase, p_judge_tag, p_draw_reason
) RETURNS TABLE(ok boolean, new_move_no bigint, error text)
```

**服务端校验逻辑**：
1. 验证 `game_id` 对局为 active 且轮到 `p_side` 走子
2. 验证 `move_no` 匹配（乐观锁，防并发冲突）
3. 若 `judge_tag = 'illegal_long_check'` → 返回 `ok=false`
4. 若 `judge_tag = 'illegal_long_chase'` → 返回 `ok=false`
5. 检查棋钟超时（服务端权威时间）
6. 写入 `chess_moves`，更新 `chess_games`（FEN/棋钟/状态）
7. 若 `draw_reason` 非空 → `status='draw'`

### 3.2 `cnchess_sync_timeout_if_due`

检查对局是否已超时，若是则写 `status='timeout'`，`winner=对方`。客户端在页面恢复前台时调用。

### 3.3 `get_my_room_ids`

```sql
-- SECURITY DEFINER，返回当前用户的所有 room_id
SELECT room_id FROM room_members WHERE user_id = auth.uid();
```

供 Realtime 订阅过滤使用。

---

## 4. Edge Functions

### 4.1 `approve-login`

**触发**：Talk / Sync 手机端调用（用于批准 Cloud 桌面登录）

```
POST /functions/v1/approve-login
Authorization: Bearer <用户JWT>
Body: { "sessionId": "<uuid>" }
```

**逻辑**：
1. 验证 JWT → 获取 userId
2. 查询 `qr_login_sessions` 确认 session 存在且未过期
3. 调用 Supabase Admin API `generateLink` 生成一次性 token
4. 更新 `qr_login_sessions.status = 'approved'`，写入 `login_token`
5. Cloud 桌面通过 Realtime 收到更新 → 完成登录

---

### 4.2 `weather-proxy`

**触发**：Sync 应用调用

```
GET /functions/v1/weather-proxy?lat=30.1&lon=120.1
Authorization: Bearer <用户JWT>
```

**逻辑**：
1. 验证 JWT
2. 用 EdDSA 私钥生成和风天气 JWT（`QWEATHER_PROJECT_ID` / `QWEATHER_KID` / `QWEATHER_PRIVATE_KEY_B64`）
3. 调用 `{QWEATHER_API_HOST}/v7/weather/now?location=lon,lat`
4. 调用 `{QWEATHER_API_HOST}/v7/weather/3d?location=lon,lat`
5. 合并返回

---

### 4.3 `herb-proxy`

**触发**：Doctor 应用调用

```
GET /functions/v1/herb-proxy?p=/herb/search?q=人参
Authorization: Bearer <用户JWT>
```

**逻辑**：
1. 验证 JWT
2. 取 `p` 参数作为路径
3. `GET {HERB_API_BASE}{p}?key={HERB_API_KEY}`
4. 透传响应

---

### 4.4 `cloud-files`

**触发**：Cloud 应用调用（见第 7 章详细说明）

```
POST /functions/v1/cloud-files?action=list
Authorization: Bearer <用户JWT>
Body: { "prefix": "owners/{uid}/documents/" }
```

强制前缀 `owners/{uid}/`，防越权访问。

---

## 5. Storage Bucket

| Bucket | 说明 | 访问控制 |
|--------|------|---------|
| `app-releases` | 应用安装包 | 公开可读；写入需管理员权限 |
| `cloud-user-files` | 用户网盘文件 | 通过 `cloud-files` 函数操作（S3 签名） |

---

## 6. Realtime 订阅表清单

| 表名 | 加入时机 | 使用方 |
|------|---------|--------|
| `qr_login_sessions` | 迁移 `20240101000001` | Talk / Cloud |
| `cnchess_presence` | 迁移 `20260413000102` | CnChess |
| `chess_invites` | 迁移 `20260413000102` | CnChess |
| `chess_games` | 迁移 `20260413000102` | CnChess |
| `chess_moves` | 迁移 `20260413000203` | CnChess |
| `friendships` | 迁移 `20260415234000` | Talk |

---

## 7. 迁移文件说明

| 文件 | 内容 |
|------|------|
| `20240101000001_qr_login_sessions.sql` | 扫码登录基础表 + Realtime |
| `20260413000102_cnchess_invite_game.sql` | 象棋邀请/presence/游戏表 + Realtime |
| `20260413000203_cnchess_gameplay.sql` | 走子记录表 + Realtime |
| `20260413000304_cnchess_judge_v2.sql` | 棋规字段 + make_move_v2 初版 |
| `20260414000100_cnchess_draw_offer.sql` | 和棋提议字段 + RPC 更新 |
| `20260414082906_remote_schema.sql` | 全量 Schema 快照（友谊/消息/Sync指标/天气等） |
| `20260415120000_cnchess_professional_clock.sql` | 专业棋钟逻辑改进 |
| `20260415234000_add_friendships_to_realtime.sql` | friendships 加入 Realtime |

---

*下一章：[跨模块核心流程](./10-cross-module-flows.md)*
