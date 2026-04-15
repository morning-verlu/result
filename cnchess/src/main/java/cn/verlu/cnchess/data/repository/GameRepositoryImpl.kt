package cn.verlu.cnchess.data.repository

import cn.verlu.cnchess.data.remote.dto.GameDto
import cn.verlu.cnchess.data.remote.dto.MoveDto
import cn.verlu.cnchess.data.remote.dto.ProfileDto
import cn.verlu.cnchess.data.remote.dto.toDomain
import cn.verlu.cnchess.di.IoDispatcher
import cn.verlu.cnchess.domain.chess.BoardCodec
import cn.verlu.cnchess.domain.chess.GameResult
import cn.verlu.cnchess.domain.chess.Move
import cn.verlu.cnchess.domain.chess.MoveAnnotation
import cn.verlu.cnchess.domain.chess.Position
import cn.verlu.cnchess.domain.chess.RepetitionJudge
import cn.verlu.cnchess.domain.chess.RuleEngine
import cn.verlu.cnchess.domain.chess.Side
import cn.verlu.cnchess.domain.model.ChessGame
import cn.verlu.cnchess.domain.model.ChessGameStatus
import cn.verlu.cnchess.domain.model.GameHistoryItem
import cn.verlu.cnchess.domain.model.MoveRecord
import cn.verlu.cnchess.domain.model.Profile
import cn.verlu.cnchess.util.parseTimestampToMs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ChessMoveInsertDto(
    @SerialName("game_id") val gameId: String,
    @SerialName("move_no") val moveNo: Long,
    @SerialName("side") val side: String,
    @SerialName("from_row") val fromRow: Int,
    @SerialName("from_col") val fromCol: Int,
    @SerialName("to_row") val toRow: Int,
    @SerialName("to_col") val toCol: Int,
    @SerialName("fen_before") val fenBefore: String,
    @SerialName("fen_after") val fenAfter: String,
    @SerialName("spent_ms") val spentMs: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("position_hash") val positionHash: String,
    @SerialName("is_check") val isCheck: Boolean,
    @SerialName("is_chase") val isChase: Boolean,
    @SerialName("judge_tag") val judgeTag: String?,
)

@Serializable
private data class ChessGameUpdateDto(
    val fen: String,
    @SerialName("turn_side") val turnSide: String,
    @SerialName("move_no") val moveNo: Long,
    @SerialName("red_time_ms") val redTimeMs: Long,
    @SerialName("black_time_ms") val blackTimeMs: Long,
    val status: String,
    @SerialName("winner_user_id") val winnerUserId: String?,
    @SerialName("last_move_at") val lastMoveAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("draw_reason") val drawReason: String? = null,
    @SerialName("draw_offer_by_user_id") val drawOfferByUserId: String? = null,
    @SerialName("draw_offer_at") val drawOfferAt: String? = null,
)

@Serializable
private data class ChessResignUpdateDto(
    /** 必须无默认值，否则 kotlinx.serialization 在 encodeDefaults=false 时会省略，PATCH 不会改 status 列 */
    val status: String,
    @SerialName("winner_user_id") val winnerUserId: String?,
    @SerialName("draw_offer_by_user_id") val drawOfferByUserId: String? = null,
    @SerialName("draw_offer_at") val drawOfferAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class ChessDrawOfferUpdateDto(
    @SerialName("draw_offer_by_user_id") val drawOfferByUserId: String?,
    @SerialName("draw_offer_at") val drawOfferAt: String?,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class ChessDrawAcceptUpdateDto(
    val status: String,
    @SerialName("draw_reason") val drawReason: String,
    @SerialName("draw_offer_by_user_id") val drawOfferByUserId: String? = null,
    @SerialName("draw_offer_at") val drawOfferAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class MakeMoveV2ResponseDto(
    val ok: Boolean,
    @SerialName("new_move_no") val newMoveNo: Long,
    val error: String? = null,
)

@Singleton
class GameRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : GameRepository {

    private val _gameState = MutableStateFlow<ChessGame?>(null)
    override val gameState: StateFlow<ChessGame?> = _gameState.asStateFlow()

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gameId: String? = null
    private var activeChannel: RealtimeChannel? = null
    private var fallbackSyncJob: Job? = null

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    override suspend fun bindGame(gameId: String) {
        if (this.gameId != null) {
            cleanupChannel()
        }
        this.gameId = gameId
        _gameState.value = null
        refreshGame()
        subscribeRealtime(gameId)
    }

    override suspend fun unbindGame() {
        cleanupChannel()
        gameId = null
        _gameState.value = null
    }

    override fun unbindGameFireAndForget() {
        repoScope.launch { unbindGame() }
    }

    override suspend fun refreshCurrentGame() {
        refreshGame()
    }

    private suspend fun cleanupChannel() {
        fallbackSyncJob?.cancel()
        fallbackSyncJob = null
        activeChannel?.let { ch ->
            runCatching { supabase.realtime.removeChannel(ch) }
        }
        activeChannel = null
    }

    override suspend fun makeMove(from: Position, to: Position): Unit = withContext(ioDispatcher) {
        val gid = gameId ?: error("game not bound")
        val uid = currentUserId() ?: error("未登录")
        val current = _gameState.value ?: error("对局状态未加载")
        if (current.status != ChessGameStatus.Active) error("对局已结束")
        val side = if (uid == current.redUserId) Side.Red else Side.Black
        if (current.turnSide != side) error("当前不是你的回合")

        val applied = RuleEngine.tryApplyMove(current.board, side, from, to) ?: error("非法走子")
        val nextBoard = applied.first
        val moved = applied.second
        val spentMs = 1_000L
        val nextTurn = side.opposite()
        val now = Instant.now().toString()
        val annotation = RuleEngine.buildMoveAnnotation(
            nextBoard = nextBoard,
            move = moved,
            moveNo = current.moveNo + 1,
            side = side,
            nextTurnSide = nextTurn,
        )
        val decision = RepetitionJudge.decide(current.moveHistory, annotation)
        if (decision.illegalTag != null) {
            throw IllegalStateException(
                when (decision.illegalTag) {
                    "illegal_long_check" -> "长将禁着，请变着"
                    "illegal_long_chase" -> "长捉禁着，请变着"
                    else -> "禁着"
                },
            )
        }

        val nextRedTime = if (side == Side.Red) (current.redTimeMs - spentMs).coerceAtLeast(0L) else current.redTimeMs
        val nextBlackTime = if (side == Side.Black) (current.blackTimeMs - spentMs).coerceAtLeast(0L) else current.blackTimeMs
        val result = RuleEngine.evaluateResult(nextBoard, nextTurn)
        val endedByRule = result != GameResult.Ongoing
        val timedOut = nextRedTime <= 0L || nextBlackTime <= 0L
        val nextStatus = when {
            timedOut -> "timeout"
            decision.drawReason != null -> "draw"
            endedByRule -> "finished"
            else -> "active"
        }
        val winner = when {
            timedOut && nextRedTime <= 0L -> current.blackUserId
            timedOut && nextBlackTime <= 0L -> current.redUserId
            result == GameResult.RedWin -> current.redUserId
            result == GameResult.BlackWin -> current.blackUserId
            else -> null
        }
        val sideText = if (side == Side.Red) "red" else "black"

        val fenBefore = BoardCodec.encode(current.board)
        val fenAfter = BoardCodec.encode(nextBoard)
        val newMoveNo = current.moveNo + 1
        val lastMoveAtMs = Instant.now().toEpochMilli()
        val newMoveRecord = MoveRecord(
            moveNo = newMoveNo,
            side = side,
            fromRow = from.row,
            fromCol = from.col,
            toRow = to.row,
            toCol = to.col,
            fenBefore = fenBefore,
            fenAfter = fenAfter,
            positionHash = annotation.positionHash,
            isCheck = annotation.isCheck,
            isChase = annotation.isChase,
            judgeTag = annotation.judgeTag,
        )
        val optimisticGame = current.copy(
            board = nextBoard,
            turnSide = nextTurn,
            moveNo = newMoveNo,
            redTimeMs = nextRedTime,
            blackTimeMs = nextBlackTime,
            status = toStatus(nextStatus),
            winnerUserId = winner,
            lastMove = moved,
            drawReason = decision.drawReason,
            lastMoveAtMs = lastMoveAtMs,
            moveHistory = current.moveHistory + newMoveRecord,
        )
        val baseline = current
        _gameState.value = optimisticGame

        val updateDto = ChessGameUpdateDto(
            fen = fenAfter,
            turnSide = if (nextTurn == Side.Red) "red" else "black",
            moveNo = newMoveNo,
            redTimeMs = nextRedTime,
            blackTimeMs = nextBlackTime,
            status = nextStatus,
            winnerUserId = winner,
            lastMoveAt = now,
            updatedAt = now,
            drawReason = decision.drawReason,
        )

        try {
            supabase.from("chess_moves").insert(
                ChessMoveInsertDto(
                    gameId = gid,
                    moveNo = newMoveNo,
                    side = sideText,
                    fromRow = from.row,
                    fromCol = from.col,
                    toRow = to.row,
                    toCol = to.col,
                    fenBefore = fenBefore,
                    fenAfter = fenAfter,
                    spentMs = spentMs,
                    createdAt = now,
                    positionHash = annotation.positionHash,
                    isCheck = annotation.isCheck,
                    isChase = annotation.isChase,
                    judgeTag = annotation.judgeTag,
                ),
            )

            val rpcResult = runCatching {
                supabase.from("rpc/make_move_v2").select {
                    filter {
                        eq("p_game_id", gid)
                        eq("p_move_no", current.moveNo)
                        eq("p_side", sideText)
                        eq("p_from_row", from.row)
                        eq("p_from_col", from.col)
                        eq("p_to_row", to.row)
                        eq("p_to_col", to.col)
                        eq("p_fen_before", fenBefore)
                        eq("p_fen_after", fenAfter)
                        eq("p_spent_ms", spentMs)
                        eq("p_position_hash", annotation.positionHash)
                        eq("p_is_check", annotation.isCheck)
                        eq("p_is_chase", annotation.isChase)
                        annotation.judgeTag?.let { eq("p_judge_tag", it) }
                        decision.drawReason?.let { eq("p_draw_reason", it) }
                    }
                }.decodeSingle<MakeMoveV2ResponseDto>()
            }.getOrNull()

            if (rpcResult == null || !rpcResult.ok) {
                if (rpcResult?.error == "illegal_long_check") error("长将禁着，请变着")
                if (rpcResult?.error == "illegal_long_chase") error("长捉禁着，请变着")
                if (rpcResult?.error == "draw_by_repetition") error("三次重复，判和")
                supabase.from("chess_games").update(updateDto) {
                    filter {
                        eq("id", gid)
                        eq("move_no", current.moveNo)
                        eq("turn_side", sideText)
                    }
                }
            }
            refreshGame()
        } catch (e: Throwable) {
            runCatching { refreshGame() }.onFailure { _gameState.value = baseline }
            throw e
        }
    }

    override suspend fun resign(gameId: String?): Unit = withContext(ioDispatcher) {
        val gid = gameId ?: this@GameRepositoryImpl.gameId ?: error("game not bound")
        val uid = currentUserId() ?: error("未登录")
        val row = supabase.from("chess_games").select {
            filter { eq("id", gid) }
        }.decodeSingle<GameDto>()
        if (row.status.lowercase() != "active") {
            if (this@GameRepositoryImpl.gameId == gid) refreshGame()
            return@withContext
        }
        val winnerUserId = if (row.redUserId == uid) row.blackUserId else row.redUserId
        supabase.from("chess_games").update(
            ChessResignUpdateDto(
                status = "resigned",
                winnerUserId = winnerUserId,
                updatedAt = Instant.now().toString(),
            ),
        ) {
            filter {
                eq("id", gid)
                eq("status", "active")
            }
        }
        if (this@GameRepositoryImpl.gameId == gid) {
            refreshGame()
        }
    }

    override suspend fun offerDraw(): Unit = withContext(ioDispatcher) {
        val gid = gameId ?: error("game not bound")
        val uid = currentUserId() ?: error("未登录")
        val current = _gameState.value ?: error("对局状态未加载")
        if (current.status != ChessGameStatus.Active) error("对局已结束")
        if (current.drawOfferByUserId != null) error("已有和棋请求待处理")
        val now = Instant.now().toString()
        supabase.from("chess_games").update(
            ChessDrawOfferUpdateDto(
                drawOfferByUserId = uid,
                drawOfferAt = now,
                updatedAt = now,
            ),
        ) {
            filter {
                eq("id", gid)
                eq("status", "active")
            }
        }
        refreshGame()
    }

    override suspend fun respondDraw(accept: Boolean): Unit = withContext(ioDispatcher) {
        val gid = gameId ?: error("game not bound")
        val uid = currentUserId() ?: error("未登录")
        val current = _gameState.value ?: error("对局状态未加载")
        if (current.status != ChessGameStatus.Active) error("对局已结束")
        val offerBy = current.drawOfferByUserId ?: error("当前没有和棋请求")
        if (offerBy == uid) error("不能处理自己发起的和棋请求")
        val now = Instant.now().toString()
        if (accept) {
            supabase.from("chess_games").update(
                ChessDrawAcceptUpdateDto(
                    status = "draw",
                    drawReason = "mutual_agreement",
                    updatedAt = now,
                ),
            ) {
                filter {
                    eq("id", gid)
                    eq("status", "active")
                    eq("draw_offer_by_user_id", offerBy)
                }
            }
        } else {
            supabase.from("chess_games").update(
                ChessDrawOfferUpdateDto(
                    drawOfferByUserId = null,
                    drawOfferAt = null,
                    updatedAt = now,
                ),
            ) {
                filter {
                    eq("id", gid)
                    eq("status", "active")
                    eq("draw_offer_by_user_id", offerBy)
                }
            }
        }
        refreshGame()
    }

    override suspend fun findActiveGame(): String? = withContext(ioDispatcher) {
        val uid = currentUserId() ?: return@withContext null
        runCatching {
            supabase.from("chess_games").select {
                filter {
                    eq("status", "active")
                    or {
                        eq("red_user_id", uid)
                        eq("black_user_id", uid)
                    }
                }
                order("updated_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(1)
            }.decodeList<GameDto>().firstOrNull()?.id
        }.getOrNull()
    }

    override suspend fun getActiveOpponentIds(): Set<String> = withContext(ioDispatcher) {
        val uid = currentUserId() ?: return@withContext emptySet()
        runCatching {
            supabase.from("chess_games").select {
                filter {
                    eq("status", "active")
                    or {
                        eq("red_user_id", uid)
                        eq("black_user_id", uid)
                    }
                }
            }.decodeList<GameDto>().map { g ->
                if (g.redUserId == uid) g.blackUserId else g.redUserId
            }.toSet()
        }.getOrDefault(emptySet())
    }

    override suspend fun listRecentGames(limit: Int): List<GameHistoryItem> = withContext(ioDispatcher) {
        val uid = currentUserId() ?: return@withContext emptyList()
        val games = supabase.from("chess_games").select {
            filter {
                or {
                    eq("red_user_id", uid)
                    eq("black_user_id", uid)
                }
            }
            order("updated_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<GameDto>()
        val userIds = games.flatMap { listOf(it.redUserId, it.blackUserId) }.distinct()
        val profiles = fetchProfiles(userIds)

        games.map { g ->
            val myIsRed = g.redUserId == uid
            val oppId = if (myIsRed) g.blackUserId else g.redUserId
            val opp = profiles[oppId]
            val status = toStatus(g.status)
            val resultText = when {
                status == ChessGameStatus.Draw -> "和棋"
                g.winnerUserId == null && status == ChessGameStatus.Active -> "进行中"
                g.winnerUserId == uid -> "你胜"
                g.winnerUserId == oppId -> "你负"
                else -> "已结束"
            }
            GameHistoryItem(
                gameId = g.id,
                opponentName = opp?.displayName ?: oppId.take(10),
                opponentAvatarUrl = opp?.avatarUrl,
                startedAtMs = parseTimestampToMs(g.createdAt),
                updatedAtMs = parseTimestampToMs(g.updatedAt),
                status = status,
                resultText = resultText,
            )
        }
    }

    private suspend fun refreshGame() = withContext(ioDispatcher) {
        val gid = gameId ?: return@withContext
        val uid = currentUserId() ?: return@withContext
        val game = supabase.from("chess_games").select {
            filter { eq("id", gid) }
        }.decodeSingle<GameDto>()

        val profiles = fetchProfiles(listOf(game.redUserId, game.blackUserId))
        val myProfile = profiles[uid]
        val oppId = if (uid == game.redUserId) game.blackUserId else game.redUserId
        val opponentProfile = profiles[oppId]

        val moves = runCatching {
            supabase.from("chess_moves").select {
                filter { eq("game_id", gid) }
                order("move_no", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList<MoveDto>()
        }.getOrDefault(emptyList())
        val historyAsc = moves.sortedBy { it.moveNo }.map { mv ->
            MoveRecord(
                moveNo = mv.moveNo,
                side = if (mv.side.lowercase() == "black") Side.Black else Side.Red,
                fromRow = mv.fromRow,
                fromCol = mv.fromCol,
                toRow = mv.toRow,
                toCol = mv.toCol,
                fenBefore = mv.fenBefore,
                fenAfter = mv.fenAfter,
                positionHash = mv.positionHash,
                isCheck = mv.isCheck,
                isChase = mv.isChase,
                judgeTag = mv.judgeTag,
            )
        }
        val lastMove = moves.firstOrNull()?.let { mv ->
            val board = BoardCodec.decode(game.fen)
            val piece = board.at(Position(mv.toRow, mv.toCol)) ?: return@let null
            Move(
                from = Position(mv.fromRow, mv.fromCol),
                to = Position(mv.toRow, mv.toCol),
                piece = piece,
                captured = null,
            )
        }

        val local = _gameState.value
        val serverStatus = toStatus(game.status)
        if (local != null &&
            local.id == gid &&
            local.status == ChessGameStatus.Active &&
            serverStatus == ChessGameStatus.Active &&
            local.moveNo > game.moveNo
        ) {
            // 乐观更新已领先于 DB 读到的快照时跳过覆盖，避免棋子回闪与音效重复触发
            return@withContext
        }

        _gameState.value = ChessGame(
            id = game.id,
            redUserId = game.redUserId,
            blackUserId = game.blackUserId,
            turnSide = if (game.turnSide.lowercase() == "black") Side.Black else Side.Red,
            board = BoardCodec.decode(game.fen),
            redTimeMs = game.redTimeMs,
            blackTimeMs = game.blackTimeMs,
            status = toStatus(game.status),
            winnerUserId = game.winnerUserId,
            moveNo = game.moveNo,
            lastMoveAtMs = parseTimestampToMs(game.lastMoveAt),
            drawReason = game.drawReason,
            drawOfferByUserId = game.drawOfferByUserId,
            drawOfferAtMs = parseTimestampToMs(game.drawOfferAt),
            myProfile = myProfile,
            opponentProfile = opponentProfile,
            lastMove = lastMove,
            moveHistory = historyAsc,
        )
    }

    private fun subscribeRealtime(gid: String) {
        val name = "cnchess_game_$gid"
        val channel = supabase.realtime.channel(name)
        activeChannel = channel
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "chess_games"
        }.onEach {
            repoScope.launch { runCatching { refreshGame() } }
        }.launchIn(repoScope)
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "chess_moves"
        }.onEach {
            repoScope.launch { runCatching { refreshGame() } }
        }.launchIn(repoScope)
        repoScope.launch { channel.subscribe() }
        startFallbackSync(gid)
    }

    /**
     * Realtime 断连时，定期主动拉取当前对局，确保对手走子不会长期丢失。
     */
    private fun startFallbackSync(boundGameId: String) {
        fallbackSyncJob?.cancel()
        fallbackSyncJob = repoScope.launch {
            while (isActive && gameId == boundGameId) {
                delay(1_500)
                runCatching { refreshGame() }
            }
        }
    }

    private suspend fun fetchProfiles(userIds: List<String>): Map<String, Profile> {
        if (userIds.isEmpty()) return emptyMap()
        return supabase.from("profiles").select {
            filter { isIn("id", userIds) }
        }.decodeList<ProfileDto>().associate { it.id to it.toDomain() }
    }

    private fun toStatus(status: String): ChessGameStatus = when (status.lowercase()) {
        "finished" -> ChessGameStatus.Finished
        "aborted" -> ChessGameStatus.Aborted
        "timeout" -> ChessGameStatus.Timeout
        "resigned" -> ChessGameStatus.Resigned
        "draw" -> ChessGameStatus.Draw
        else -> ChessGameStatus.Active
    }
}
