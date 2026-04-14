package cn.verlu.cnchess.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.cnchess.data.repository.GameRepository
import cn.verlu.cnchess.domain.chess.BoardCodec
import cn.verlu.cnchess.domain.chess.BoardState
import cn.verlu.cnchess.domain.chess.GameResult
import cn.verlu.cnchess.domain.chess.Move
import cn.verlu.cnchess.domain.chess.Position
import cn.verlu.cnchess.domain.chess.ReplayNavigator
import cn.verlu.cnchess.domain.chess.RuleEngine
import cn.verlu.cnchess.domain.chess.Side
import cn.verlu.cnchess.domain.model.ChessGame
import cn.verlu.cnchess.domain.model.ChessGameStatus
import cn.verlu.cnchess.domain.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChessSoundEvent { MOVE, CHECK, WIN, LOSE, DRAW }

data class GameUiState(
    val isLoading: Boolean = true,
    val game: ChessGame? = null,
    val mySide: Side = Side.Red,
    val myProfile: Profile? = null,
    val opponentProfile: Profile? = null,
    val selected: Position? = null,
    val legalTargets: Set<Position> = emptySet(),
    val localMyTimeMs: Long = 0L,
    val localOpponentTimeMs: Long = 0L,
    val totalMyTimeMs: Long = 0L,
    val totalOpponentTimeMs: Long = 0L,
    val statusText: String = "准备中",
    val checkHint: String? = null,
    val judgeHint: String? = null,
    val resultBannerText: String? = null,
    val isWin: Boolean? = null,
    val drawOfferByUserId: String? = null,
    val isIncomingDrawOffer: Boolean = false,
    val isPendingDrawOffer: Boolean = false,
    val isReplayMode: Boolean = false,
    val replayIndex: Int = 0,
    val replayTotal: Int = 0,
    val replayBoard: BoardState? = null,
    val replayMoveText: String = "第 0 手 / 0",
    val error: String? = null,
)

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _soundEvents = Channel<ChessSoundEvent>(Channel.BUFFERED)
    val soundEvents = _soundEvents.receiveAsFlow()

    private var boundGameId: String? = null
    private var autoReplayOnNextGame = false

    // Track previous game state to detect transitions for sound events
    private var prevMoveNo = -1L
    private var prevStatus: ChessGameStatus? = null

    init {
        viewModelScope.launch {
            gameRepository.gameState.collect { game ->
                val uid = supabase.auth.currentUserOrNull()?.id
                val mySide = when (uid) {
                    game?.redUserId -> Side.Red
                    game?.blackUserId -> Side.Black
                    else -> Side.Red
                }
                val myUserId = if (mySide == Side.Red) game?.redUserId else game?.blackUserId
                val opponentUserId = if (mySide == Side.Red) game?.blackUserId else game?.redUserId

                // Detect sound events only when we have a previous reference point
                if (game != null && prevStatus != null) {
                    val soundEvent: ChessSoundEvent? = when {
                        prevStatus == ChessGameStatus.Active && game.status != ChessGameStatus.Active -> {
                            when (game.status) {
                                ChessGameStatus.Draw -> ChessSoundEvent.DRAW
                                else -> {
                                    val iWin = when {
                                        game.winnerUserId != null -> game.winnerUserId == myUserId
                                        game.status == ChessGameStatus.Finished -> {
                                            val r = RuleEngine.evaluateResult(game.board, game.turnSide)
                                            (r == GameResult.RedWin && mySide == Side.Red) ||
                                                    (r == GameResult.BlackWin && mySide == Side.Black)
                                        }
                                        else -> false
                                    }
                                    if (iWin) ChessSoundEvent.WIN else ChessSoundEvent.LOSE
                                }
                            }
                        }
                        game.status == ChessGameStatus.Active && prevMoveNo >= 0 && game.moveNo != prevMoveNo -> {
                            val isCheck = RuleEngine.isInCheck(game.board, game.turnSide)
                            if (isCheck) ChessSoundEvent.CHECK else ChessSoundEvent.MOVE
                        }
                        else -> null
                    }
                    soundEvent?.let { _soundEvents.trySend(it) }
                }

                // Update tracking variables
                prevMoveNo = game?.moveNo ?: -1L
                prevStatus = game?.status

                // Compute result banner info
                val resultBannerText: String?
                val isWin: Boolean?
                if (game != null && game.status != ChessGameStatus.Active) {
                    resultBannerText = when (game.status) {
                        ChessGameStatus.Draw -> "和棋"
                        ChessGameStatus.Aborted -> "对局中止"
                        ChessGameStatus.Resigned -> when {
                            game.winnerUserId == myUserId -> "对手认输，你赢了"
                            game.winnerUserId != null -> "你认输了"
                            else -> "认输结束"
                        }
                        ChessGameStatus.Timeout -> when {
                            game.winnerUserId == myUserId -> "对手超时，你赢了"
                            game.winnerUserId != null -> "你超时，对手赢了"
                            else -> "超时结束"
                        }
                        ChessGameStatus.Finished -> {
                            if (game.winnerUserId != null) {
                                if (game.winnerUserId == myUserId) "你赢了" else "你输了"
                            } else {
                                val r = RuleEngine.evaluateResult(game.board, game.turnSide)
                                val iWin = (r == GameResult.RedWin && mySide == Side.Red) ||
                                        (r == GameResult.BlackWin && mySide == Side.Black)
                                val opponentWin = (r == GameResult.RedWin && mySide == Side.Black) ||
                                        (r == GameResult.BlackWin && mySide == Side.Red)
                                when {
                                    iWin -> "你赢了"
                                    opponentWin -> "你输了"
                                    else -> "对局结束"
                                }
                            }
                        }
                        else -> "对局结束"
                    }
                    isWin = when (game.status) {
                        ChessGameStatus.Draw, ChessGameStatus.Aborted -> null
                        else -> when {
                            game.winnerUserId == myUserId -> true
                            game.winnerUserId != null -> false
                            game.status == ChessGameStatus.Finished -> {
                                val r = RuleEngine.evaluateResult(game.board, game.turnSide)
                                (r == GameResult.RedWin && mySide == Side.Red) ||
                                        (r == GameResult.BlackWin && mySide == Side.Black)
                            }
                            else -> null
                        }
                    }
                } else {
                    resultBannerText = null
                    isWin = null
                }

                _state.update {
                    val replayTotal = game?.moveHistory?.size ?: 0
                    val shouldAutoReplay = autoReplayOnNextGame && game != null && !it.isReplayMode
                    val replayIndex = when {
                        shouldAutoReplay -> 0
                        it.isReplayMode -> it.replayIndex.coerceIn(0, replayTotal)
                        else -> replayTotal
                    }
                    val replayBoard = when {
                        shouldAutoReplay && game != null -> ReplayNavigator.boardAt(game.moveHistory, 0)
                        it.isReplayMode -> it.replayBoard
                        else -> null
                    }
                    it.copy(
                        isLoading = game == null,
                        game = game,
                        mySide = mySide,
                        myProfile = game?.myProfile,
                        opponentProfile = game?.opponentProfile,
                        localMyTimeMs = if (mySide == Side.Red) game?.redTimeMs ?: 0L else game?.blackTimeMs ?: 0L,
                        localOpponentTimeMs = if (mySide == Side.Red) game?.blackTimeMs ?: 0L else game?.redTimeMs ?: 0L,
                        totalMyTimeMs = when {
                            it.totalMyTimeMs > 0L -> it.totalMyTimeMs
                            mySide == Side.Red -> game?.redTimeMs ?: 0L
                            else -> game?.blackTimeMs ?: 0L
                        },
                        totalOpponentTimeMs = when {
                            it.totalOpponentTimeMs > 0L -> it.totalOpponentTimeMs
                            mySide == Side.Red -> game?.blackTimeMs ?: 0L
                            else -> game?.redTimeMs ?: 0L
                        },
                        statusText = statusText(game, mySide),
                        checkHint = game?.let { g ->
                            if (g.status == ChessGameStatus.Active && RuleEngine.isInCheck(g.board, g.turnSide)) "将军" else null
                        },
                        judgeHint = game?.drawReason?.let { reason ->
                            when (reason) {
                                "threefold_repetition" -> "三次重复判和"
                                "mutual_agreement" -> "双方同意和棋"
                                else -> reason
                            }
                        },
                        drawOfferByUserId = game?.drawOfferByUserId,
                        isIncomingDrawOffer = game?.status == ChessGameStatus.Active &&
                                game.drawOfferByUserId != null &&
                                game.drawOfferByUserId == opponentUserId,
                        isPendingDrawOffer = game?.status == ChessGameStatus.Active &&
                                game.drawOfferByUserId != null &&
                                game.drawOfferByUserId == myUserId,
                        resultBannerText = resultBannerText,
                        isWin = isWin,
                        isReplayMode = shouldAutoReplay || it.isReplayMode,
                        replayIndex = replayIndex,
                        replayBoard = replayBoard,
                        replayTotal = replayTotal,
                        replayMoveText = "第 ${if (shouldAutoReplay || it.isReplayMode) replayIndex else replayTotal} 手 / $replayTotal",
                    )
                }
                if (autoReplayOnNextGame && game != null) {
                    autoReplayOnNextGame = false
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                delay(1_000)
                tickClock()
            }
        }
    }

    fun bind(gameId: String, startInReplayMode: Boolean = false) {
        autoReplayOnNextGame = startInReplayMode
        if (boundGameId == gameId) return
        prevMoveNo = -1L
        prevStatus = null
        boundGameId = gameId
        _state.value = GameUiState()
        viewModelScope.launch {
            runCatching { gameRepository.bindGame(gameId) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "加载对局失败") } }
        }
    }

    fun onBoardTap(pos: Position) {
        val game = _state.value.game ?: return
        if (game.status != ChessGameStatus.Active) return
        if (_state.value.isReplayMode) return

        val selected = _state.value.selected
        if (selected == null) {
            selectIfOwnPiece(pos, game)
            return
        }

        if (selected == pos) {
            _state.update { it.copy(selected = null, legalTargets = emptySet()) }
            return
        }

        if (_state.value.legalTargets.contains(pos)) {
            submitMove(selected, pos)
            _state.update { it.copy(selected = null, legalTargets = emptySet()) }
            return
        }

        selectIfOwnPiece(pos, game)
    }

    fun resign() {
        viewModelScope.launch {
            runCatching { gameRepository.resign() }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "认输失败") } }
        }
    }

    fun requestDraw() {
        viewModelScope.launch {
            runCatching { gameRepository.offerDraw() }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "发起和棋失败") } }
        }
    }

    fun acceptDraw() {
        viewModelScope.launch {
            runCatching { gameRepository.respondDraw(accept = true) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "同意和棋失败") } }
        }
    }

    fun rejectDraw() {
        viewModelScope.launch {
            runCatching { gameRepository.respondDraw(accept = false) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "拒绝和棋失败") } }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun toggleReplayMode() {
        val game = _state.value.game ?: return
        if (_state.value.isReplayMode) {
            _state.update {
                it.copy(
                    isReplayMode = false,
                    replayBoard = null,
                    replayIndex = game.moveHistory.size,
                    replayMoveText = "第 ${game.moveHistory.size} 手 / ${game.moveHistory.size}",
                )
            }
        } else {
            val board = ReplayNavigator.boardAt(game.moveHistory, 0)
            _state.update {
                it.copy(
                    isReplayMode = true,
                    replayIndex = 0,
                    replayBoard = board,
                    replayTotal = game.moveHistory.size,
                    replayMoveText = "第 0 手 / ${game.moveHistory.size}",
                )
            }
        }
    }

    fun replayToStart() {
        val game = _state.value.game ?: return
        if (!_state.value.isReplayMode) return
        val board = ReplayNavigator.boardAt(game.moveHistory, 0)
        _state.update {
            it.copy(
                replayIndex = 0,
                replayBoard = board,
                replayMoveText = "第 0 手 / ${game.moveHistory.size}",
            )
        }
    }

    fun replayPrev() {
        val game = _state.value.game ?: return
        if (!_state.value.isReplayMode) return
        val idx = (_state.value.replayIndex - 1).coerceAtLeast(0)
        updateReplayByIndex(game, idx)
    }

    fun replayNext() {
        val game = _state.value.game ?: return
        if (!_state.value.isReplayMode) return
        val idx = (_state.value.replayIndex + 1).coerceAtMost(game.moveHistory.size)
        updateReplayByIndex(game, idx)
    }

    fun replayToEnd() {
        val game = _state.value.game ?: return
        if (!_state.value.isReplayMode) return
        updateReplayByIndex(game, game.moveHistory.size)
    }

    override fun onCleared() {
        gameRepository.unbindGameFireAndForget()
        super.onCleared()
    }

    private fun selectIfOwnPiece(pos: Position, game: ChessGame) {
        val piece = game.board.at(pos) ?: return
        if (piece.side != _state.value.mySide) return
        if (game.turnSide != _state.value.mySide) return
        val legal = RuleEngine.legalMovesFrom(game.board, _state.value.mySide, pos).map { it.to }.toSet()
        _state.update { it.copy(selected = pos, legalTargets = legal) }
    }

    private fun submitMove(from: Position, to: Position) {
        val game = _state.value.game ?: return
        if (game.turnSide != _state.value.mySide) return
        val valid = RuleEngine.tryApplyMove(game.board, _state.value.mySide, from, to) != null
        if (!valid) {
            _state.update { it.copy(error = "非法走子") }
            return
        }
        viewModelScope.launch {
            runCatching { gameRepository.makeMove(from, to) }
                .onFailure { e ->
                    if (e is CancellationException) return@onFailure
                    val msg = when (e) {
                        is IllegalStateException -> e.message ?: "走子失败"
                        else -> "网络异常，走子已撤销，请检查网络后重试"
                    }
                    _state.update { it.copy(error = msg) }
                }
        }
    }

    private fun tickClock() {
        val game = _state.value.game ?: return
        if (game.status != ChessGameStatus.Active) return
        if (_state.value.isReplayMode) return
        val myTurn = game.turnSide == _state.value.mySide
        if (myTurn) {
            _state.update { it.copy(localMyTimeMs = (it.localMyTimeMs - 1_000).coerceAtLeast(0L)) }
        } else {
            _state.update { it.copy(localOpponentTimeMs = (it.localOpponentTimeMs - 1_000).coerceAtLeast(0L)) }
        }
    }

    private fun statusText(game: ChessGame?, mySide: Side): String {
        if (game == null) return "加载中"
        return when (game.status) {
            ChessGameStatus.Active -> if (game.turnSide == mySide) "轮到你走" else "等待对手"
            ChessGameStatus.Timeout -> "超时结束"
            ChessGameStatus.Resigned -> "认输结束"
            ChessGameStatus.Draw -> "和棋"
            ChessGameStatus.Finished -> {
                val result = RuleEngine.evaluateResult(game.board, game.turnSide)
                when (result) {
                    GameResult.RedWin -> "红方胜"
                    GameResult.BlackWin -> "黑方胜"
                    else -> "对局结束"
                }
            }
            ChessGameStatus.Aborted -> "对局中止"
        }
    }

    private fun updateReplayByIndex(game: ChessGame, idx: Int) {
        val board = ReplayNavigator.boardAt(game.moveHistory, idx)
        _state.update {
            it.copy(
                replayIndex = idx,
                replayBoard = board,
                replayMoveText = "第 $idx 手 / ${game.moveHistory.size}",
            )
        }
    }
}
