# 跨模块核心流程

> 本章梳理平台中涉及多个应用协作的关键业务流程，帮助理解模块间的数据依赖关系。

---

## 1. 统一账号注册与登录

**涉及模块**：全部应用（Sync / Talk / Doctor / CnChess / Music / Cloud）

所有应用共享同一套 Supabase Auth，用户在任一应用注册后，凭相同邮箱密码可登录所有应用。

```
用户输入邮箱 → AuthEmailRoute
      │
      ├── 已有账号：输入密码 → AuthPasswordRoute
      │       │
      │       ▼
      │   supabase.auth.signInWithPassword(email, password)
      │
      └── 新用户：填写密码注册 → signUp(email, password)
                │
                ▼
         auth.users 新增记录
                │
                ▼（触发器 on_auth_user_created）
         profiles 自动创建行（display_name 取自邮箱前缀）
                │
                ▼
         PKCE 会话建立 → 各应用 AuthSessionViewModel 感知
                │
                ▼
         isInitializing=false, isAuthenticated=true → 进入主界面
```

**密码重置**：
```
Auth 页面 → 点击"忘记密码" → signIn with PKCE link → 邮件
      │
收到邮件 → 点击链接 → 深链回调（如 talkapp://login#...）
      │
supabase.auth.exchangeCodeForSession() → 恢复会话
      │
UpdatePasswordDialog 弹出 → 输入新密码 → updateUser(password: xxx)
```

---

## 2. 扫码授权登录（手机→桌面）

**涉及模块**：Talk（手机扫码）← → Cloud Desktop（桌面展示 QR）

```
┌─────────────────────────────────────────────────────────────────┐
│                         时序图                                   │
│                                                                 │
│  Cloud Desktop          Supabase              Talk (手机)       │
│      │                     │                      │            │
│      │ INSERT qr_session   │                      │            │
│      │ status='pending'    │                      │            │
│      │────────────────────►│                      │            │
│      │                     │                      │            │
│      │ 展示 QR（含sessionId）│                      │            │
│      │                     │                      │            │
│      │ Realtime SUBSCRIBE  │                      │            │
│      │ qr_login_sessions   │                      │            │
│      │────────────────────►│                      │            │
│      │                     │                      │            │
│      │                     │◄─────────────────────│            │
│      │                     │  扫码解析 sessionId   │            │
│      │                     │                      │            │
│      │                     │◄─────────────────────│            │
│      │                     │  POST approve-login  │            │
│      │                     │  (JWT + sessionId)   │            │
│      │                     │                      │            │
│      │                     │ 验证 JWT              │            │
│      │                     │ generateLink(token)  │            │
│      │                     │ UPDATE session       │            │
│      │                     │ status='approved'    │            │
│      │                     │ login_token=xxx      │            │
│      │                     │                      │            │
│      │◄────────────────────│                      │            │
│      │ Realtime UPDATE     │                      │            │
│      │ 收到 login_token     │                      │            │
│      │                     │                      │            │
│      │ exchangeToken(token)│                      │            │
│      │────────────────────►│                      │            │
│      │                     │                      │            │
│      │◄────────────────────│                      │            │
│      │ 会话建立成功          │                      │            │
│      │ → 进入 ExplorerScreen│                      │            │
└─────────────────────────────────────────────────────────────────┘
```

**关键安全保障**：
- QR 二维码包含 `sessionId`，本身无任何凭证
- 只有携带有效 JWT 的手机端可调用 `approve-login`
- `login_token` 是 Supabase 生成的一次性 token，不可复用
- session 有 `expires_at`，过期后 QR 自动失效

---

## 3. 好友系统（Talk ↔ CnChess 共享）

**涉及模块**：Talk（管理好友）、CnChess（好友对局）

`friendships` 表由 Talk 管理（搜索、申请、同意、拒绝），CnChess 直接读取同一张表展示好友列表并发起象棋邀请。

```
Talk：用户 A 添加用户 B 为好友（接受申请后）
      │
      ▼
数据库：friendships.status = 'accepted', room_id = <新房间>
      │
      │──────────────────────────────────────────────────┐
      ▼                                                  ▼
Talk：                                             CnChess：
好友出现在联系人列表                               好友出现在首页列表
可发起聊天（进入 room）                            可发起象棋邀请
```

**好友关系的 CnChess 使用**：
- 首页 `FriendsScreen` 直接查询 `friendships`（accepted + 自身为一方）
- `cnchess_presence` RLS：只有已接受好友的在线状态可见
- 邀请发送：目标方必须在好友列表中

---

## 4. 象棋邀请与对局建立

**涉及模块**：CnChess（全流程）+ 共享 `friendships`

```
用户 A 在好友列表点击「邀请对局」
      │
ChessInviteRepository.sendInvite(toUserId)
      │  INSERT chess_invites(from=A, to=B, status='pending')
      ▼
B 的 InviteListenerViewModel 收到 Realtime INSERT 事件
      │
IncomingInviteDialog 弹出（带接受/拒绝按钮）
      │
      ├── 拒绝 → UPDATE status='rejected' → A 收到通知
      │
      └── 接受 → acceptInvite(inviteId)
              │  服务端：INSERT chess_games(red=A, black=B)
              │  服务端：UPDATE chess_invites.game_id = newGameId, status='accepted'
              ▼
          A 和 B 的 ActiveGameViewModel.checkOnce() 检测到进行中对局
              │
              ▼
          自动导航到 GameScreen(gameId)
              │
              ▼
          对局开始（棋盘初始化，红方先走）
```

---

## 5. 应用版本发布与更新

**涉及模块**：Admin Web（发布）← → 全部 Android 应用（检查）

```
┌─────────────────────────────────────────────────────────────────┐
│                         发布侧（管理员）                          │
│                                                                 │
│  Admin Web                                                      │
│  1. 填写版本信息 + 选择安装包                                      │
│  2. 上传 APK 到 Storage bucket: app-releases                    │
│  3. INSERT app_releases（含 download_url）                       │
│  4. 旧版本自动 enabled=false（触发器）                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ 实时（Realtime 或下次启动）
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         消费侧（用户）                            │
│                                                                 │
│  Android 应用启动                                               │
│  AppUpdateGate → 查询 app_releases                              │
│  WHERE package_name = '当前包名'                                 │
│    AND enabled = true                                           │
│                                                                 │
│  检查结果：                                                       │
│  ├── 无记录 / 版本相同 → 正常启动                                  │
│  ├── 有新版本 + force=false → 可跳过更新弹窗                       │
│  └── 有新版本 + force=true → 不可跳过 → 下载 download_url → 安装 │
└─────────────────────────────────────────────────────────────────┘
```

**灰度发布**：`is_rollout=true` 的版本只对特定用户可见（可配合服务端逻辑决定哪些 uid 命中灰度）。

---

## 6. 天气数据流（Sync → 和风 API）

**涉及模块**：Sync（消费）→ Edge Function（代理）→ 第三方（和风天气）

```
Sync 应用前台
      │
定位模块获取经纬度（Play Services Location）
      │
WeatherRepositoryImpl.fetchWeather(lat, lon)
      │  检查 Room 缓存是否过期
      │
      ├── 未过期 → 直接返回缓存
      │
      └── 已过期 → GET /functions/v1/weather-proxy?lat=xxx&lon=xxx
                  │  Header: Authorization: Bearer <JWT>
                  ▼
              weather-proxy 函数
                  │  EdDSA 签名 → 和风天气 API
                  ▼
              返回 JSON（now + 3d 预报）
                  │
                  ▼
              写入 Room WeatherSnapshot + 上传 weather_snapshots 表
                  │
                  ▼
              WeatherViewModel → UI 刷新天气卡片
```

---

## 7. 中医内容访问（Doctor → zyapi）

**涉及模块**：Doctor（消费）→ Edge Function（代理）→ 第三方（本草 API）

```
Doctor 应用搜索/浏览文章
      │
HerbRepository.searchHerbs(query)
      │  先查 Room 缓存
      │
      ├── 命中缓存 → 返回缓存数据（离线可用）
      │
      └── 未命中 → GET /functions/v1/herb-proxy?p=/herb/search?q=xxx
                  │  Header: Authorization: Bearer <JWT>
                  ▼
              herb-proxy 函数
                  │  附上 HERB_API_KEY → 转发到 HERB_API_BASE
                  ▼
              第三方本草 API 响应
                  │
                  ▼
              写入 Room 缓存
                  │
                  ▼
              ViewModel → UI 展示文章列表
```

---

## 8. 跨模块依赖总结

```
┌──────────────────────────────────────────────────────────────────┐
│                     数据依赖关系图                                 │
│                                                                  │
│  Talk ──────────────► friendships ◄────────── CnChess           │
│  (管理好友)              (共享表)              (读取好友)           │
│                                                                  │
│  Talk ──────────────► qr_login_sessions ◄──── Cloud Desktop     │
│  (批准登录)            (共享表)              (发起/等待)            │
│                                                                  │
│  Admin Web ─────────► app_releases ◄────── 全部 Android 应用     │
│  (发布版本)             (共享表)              (检查更新)            │
│                                                                  │
│  所有应用 ────────────► profiles / auth.users                     │
│  (使用同一账号)          (共享认证体系)                              │
│                                                                  │
│  Sync ──────────────► weather-proxy ────────► 和风天气 API        │
│  Doctor ────────────► herb-proxy ───────────► 本草 API           │
│  Cloud ─────────────► cloud-files ──────────► S3 存储            │
│  Talk/Sync ─────────► approve-login ────────► qr_login_sessions  │
└──────────────────────────────────────────────────────────────────┘
```

---

*文档完毕。如需生成 PDF，请运行 [build-docs.ps1](../scripts/build-docs.ps1)。*
