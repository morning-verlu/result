# CnChess 应用模块详解

> 包名：`cn.verlu.cnchess` · 模块路径：`cnchess/` · 平台：Android（API 33+）

---

## 1. 模块定位

**CnChess** 是平台的**在线中国象棋**对战应用。好友间可发起邀请、进行棋局、受棋钟约束实时走子，服务端通过 RPC 函数完成走子校验、棋钟扣时、超时判负和棋等规则裁决。还支持历史对局回放与复盘。

---

## 2. 核心功能

| 功能 | 说明 |
|------|------|
| 好友管理 | 显示已接受好友列表，共享 `friendships` 表（与 Talk 同库） |
| 邀请系统 | 向好友发送对局邀请，Realtime 实时通知对方 |
| 在线状态 | `cnchess_presence` 表记录用户在线/前台/心跳时间 |
| 棋盘对局 | 完整中国象棋走子、吃子、将军、将死、困毙判断 |
| 服务端棋钟 | 服务端 `make_move_v2` RPC 扣除思考时间，防客户端作弊 |
| 超时判负 | `cnchess_sync_timeout_if_due` RPC 检查超时并写结果 |
| 禁手判断 | 长将/长捉 → 禁着提示；三次重复 → 判和 |
| 和棋提议 | 一方提议和棋，对方可接受/拒绝 |
| 认输 | 主动认输，服务端写 `resigned` 状态 |
| 历史对局 | `GameHistoryScreen` 列表，含结果与时间 |
| 棋局回放 | `Game(gameId, startInReplayMode=true)` 逐步复盘 |
| 应用更新 | 从 `app_releases` 检查新版本 |

---

## 3. 页面结构与路由

```
CnChessNavApp
│
├── Splash（isInitializing=true 期间）
├── Auth 子流（未登录）
│   ├── AuthRoute
│   ├── AuthEmailRoute
│   └── AuthPasswordRoute
│
└── Home（已登录）
    ├── HomeScreen（好友列表主页）
    │   └── IncomingInviteDialog（收到邀请时弹出）
    ├── ProfileScreen
    ├── GameHistoryScreen
    └── GameScreen(gameId, startInReplayMode)
```

**后台监听**：`CnChessNavApp` 在 `LaunchedEffect` 中持续运行：
- `InviteListenerViewModel` → Realtime 监听 `chess_invites`
- `ActiveGameViewModel.checkOnce()` → 检查是否有进行中对局（冷启动自动续局）
- 每 30 秒 `inviteVm.heartbeat()` → 更新 `cnchess_presence` 在线时间戳

---

## 4. 棋局数据架构

CnChess **不使用 Room**，所有对局状态均以 `StateFlow<ChessGame?>` 形式存于内存，服务端为单一数据源。

### 4.1 核心数据模型

```kotlin
data class ChessGame(
    val id: String,
    val redUserId / blackUserId: String,
    val board: BoardState,       // 10×9 棋盘状态
    val turnSide: Side,          // 当前行棋方 Red/Black
    val redTimeMs / blackTimeMs: Long,  // 剩余棋钟
    val status: ChessGameStatus, // Active/Finished/Resigned/Draw/Timeout
    val moveHistory: List<MoveRecord>,
    ...
)
```

### 4.2 云端数据表

| 表名 | 说明 |
|------|------|
| `cnchess_presence` | 用户在线状态（user_id, status, last_heartbeat） |
| `chess_invites` | 邀请记录（from/to userId, status, game_id） |
| `chess_games` | 对局主表（FEN, 棋钟, 状态, 走子方, 和棋字段等） |
| `chess_moves` | 每步走子记录（含 FEN 前后、棋钟、标签） |

---

## 5. 走子全流程

```
用户点击落子目标格
      │
GameViewModel.onDropPiece(from, to)
      │
RuleEngine.tryApplyMove(board, side, from, to)
      │  ← 非法走子立即拒绝（不出网络请求）
      ▼
buildMoveAnnotation(nextBoard, move, ...)
      │  计算 isCheck / isChase / positionHash / judgeTag
      ▼
RepetitionJudge.decide(history, annotation)
      │  检查：长将→禁着 / 长捉（含重复局面）→禁着 / 三次重复→和棋
      │  ← 违规时立即抛异常，UI 显示提示
      ▼
乐观更新：_gameState.value = optimisticGame（棋盘立即响应）
      │
      ▼
make_move_v2 RPC（Supabase）
      │  服务端再次校验：move_no, turn_side, fen_before
      │  服务端扣除棋钟：spent_ms（不信任客户端时间）
      │  服务端写 chess_moves
      │  服务端更新 chess_games（状态/棋钟/FEN）
      ▼
RPC 响应
  ok=true  → refreshGame()（同步服务端最终状态）
  ok=false → 解析错误码 → 回滚 UI + 提示
  网络异常 → fallback 直接写库 + refreshGame()
```

---

## 6. 棋规引擎（客户端）

位于 `cnchess/src/main/java/cn/verlu/cnchess/domain/chess/`：

### 6.1 RuleEngine

| 方法 | 功能 |
|------|------|
| `legalMovesFrom(board, side, from)` | 返回合法着法列表（包含过滤自将） |
| `tryApplyMove(board, side, from, to)` | 验证并应用走子，返回新棋盘状态 |
| `isInCheck(board, side)` | 判断指定方是否被将军 |
| `hasAnyLegalMove(board, side)` | 判断是否存在合法走法（困毙检测） |
| `evaluateResult(board, turnSide)` | 评估对局结果（将死/困毙/继续） |
| `buildMoveAnnotation(...)` | 构建走子注解（将军/捉/局面哈希） |
| `isChaseMove(board, move)` | 判断是否为捉子走法（捉车/炮/马/将） |

**棋子走法规则**：王/仕/象/马/车/炮/兵均完整实现，含蹩马腿、象眼、炮需翻山、兵过河横走、将帅照面等规则。

### 6.2 RepetitionJudge

```kotlin
fun decide(history, annotation): JudgeDecision
```

**判定优先级**（按序，先命中先返回）：

1. **长将禁着**：当前方连续 3 步均为将军 → `illegal_long_check`
2. **长捉禁着**：当前方连续 3 步均为捉子，**且**局面已达三次重复 → `illegal_long_chase`
3. **三次重复和棋**：相同局面（positionHash）出现 3 次 → `threefold_repetition`

> **关键设计**：长捉必须叠加"局面重复"条件，避免普通持续施压被误判为禁手。

### 6.3 BoardCodec

- `encode(BoardState): String` → FEN 格式字符串（服务端兼容）
- `decode(String): BoardState` → 从 FEN 恢复棋盘
- `initialBoard(): BoardState` → 标准开局布局

---

## 7. 棋钟机制

```
服务端存储：chess_games.red_time_ms / black_time_ms（剩余时间，毫秒）

客户端计算已用时间：
  spentMs = now() - lastMoveAt（或 createdAt）

服务端 make_move_v2 接收 p_spent_ms，扣除后更新棋钟
  nextRedTimeMs = redTimeMs - spentMs （若 side=red）

超时检测：
  cnchess_sync_timeout_if_due(game_id, user_id, current_time)
  → 服务端判断是否超时，写 status='timeout', winner=对方
```

---

## 8. 和棋提议流程

```
方A 点击"提议和棋"
      │
GameViewModel.offerDraw()
      │  update chess_games set draw_offer_by_user_id = uid
      ▼
方B Realtime 感知 draw_offer_by_user_id 不为空 → UI 弹出和棋提议弹窗
      │
      ├── 接受 → make_move_v2（p_draw_reason='mutual_agreement'）→ status='draw'
      └── 拒绝 → update draw_offer_by_user_id = null
```

---

## 9. 回放模式

```
GameScreen(gameId, startInReplayMode=true)
      │
ReplayControllerViewModel 加载 moveHistory
      │
逐步前进/后退走子（纯客户端计算，不发网络请求）
      │
GameScreen 同步渲染每步棋盘状态
```

---

## 10. 单元测试

位于 `cnchess/src/test/java/cn/verlu/cnchess/`：

| 测试类 | 覆盖内容 |
|--------|---------|
| `RuleEngineTest` | 合法走法、将军过滤、编解码往返 |
| `RepetitionJudgeTest` | 三次重复/长将/长捉判断、优先级 |
| `ReplayControllerTest` | 回放步进/回退逻辑 |

运行命令：
```bash
./gradlew :cnchess:testDebugUnitTest
```

---

## 11. 权限要求

| Android 权限 | 用途 |
|-------------|------|
| `INTERNET` | 网络请求 |

最低 SDK：**API 33**（`minSdk = 33`）

---

## 12. 配置文件

```kotlin
// cnchess/src/main/java/cn/verlu/cnchess/data/remote/SupabaseConfig.kt
object SupabaseConfig {
    const val URL = "https://jlzfvxxwzcpvtzdemcpm.supabase.co"
    const val ANON_KEY = "..."
}
```

---

## 13. 模块依赖

```
cnchess
├── Supabase：Auth + Postgrest + Realtime（presence/invite/game）
├── Hilt：AppModule
├── Navigation3：CnChessNavApp
├── 自研 RuleEngine + RepetitionJudge + BoardCodec（domain/chess/）
├── （不使用 Room，所有状态在内存）
└── （通过 Supabase 共享 friendships 表与 Talk 好友数据）
```

---

*下一章：[Cloud 模块详解](./07-cloud.md)*
