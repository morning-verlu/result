package cn.verlu.cnchess.presentation.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.cnchess.domain.chess.Piece
import cn.verlu.cnchess.domain.chess.PieceType
import cn.verlu.cnchess.domain.chess.Position
import cn.verlu.cnchess.domain.chess.Side
import cn.verlu.cnchess.domain.model.ChessGameStatus
import cn.verlu.cnchess.domain.model.Profile
import cn.verlu.cnchess.presentation.navigation.LocalSnackbarHostState
import cn.verlu.cnchess.presentation.ui.CnChessLoadingIndicator
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GameScreen(
    gameId: String,
    startInReplayMode: Boolean = false,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    var showResignOnBackDialog by remember { mutableStateOf(false) }
    var showResignDialog by remember { mutableStateOf(false) }
    var showDrawDialog by remember { mutableStateOf(false) }

    // Sound effects
    val soundManager = remember { ChessSoundManager() }
    DisposableEffect(context) {
        soundManager.init(context)
        onDispose { soundManager.release() }
    }
    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                ChessSoundEvent.CHECK -> soundManager.playCheck()
                else -> Unit
            }
        }
    }

    val isGameActive = state.game?.status == ChessGameStatus.Active

    // Intercept back when game is active and not in replay – resign confirmation
    BackHandler(enabled = isGameActive && !state.isReplayMode) {
        showResignOnBackDialog = true
    }

    // Resign-on-back dialog
    if (showResignOnBackDialog) {
        AlertDialog(
            onDismissRequest = { showResignOnBackDialog = false },
            title = { Text("退出对局") },
            text = { Text("退出将视为认输，对局立即结束。确认退出吗？") },
            confirmButton = {
                Button(onClick = {
                    showResignOnBackDialog = false
                    viewModel.resignAndExit(onBack)
                }) { Text("确认退出") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResignOnBackDialog = false }) { Text("继续下棋") }
            },
        )
    }

    // Resign confirmation dialog
    if (showResignDialog) {
        AlertDialog(
            onDismissRequest = { showResignDialog = false },
            title = { Text("确认认输") },
            text = { Text("确认认输？对局将立即结束，你方判负。") },
            confirmButton = {
                Button(onClick = {
                    showResignDialog = false
                    viewModel.resignAndExit(onBack)
                }) { Text("确认认输") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResignDialog = false }) { Text("取消") }
            },
        )
    }

    // Draw offer confirmation dialog
    if (showDrawDialog) {
        AlertDialog(
            onDismissRequest = { showDrawDialog = false },
            title = { Text("提议和棋") },
            text = { Text("确认向对手发出和棋提议？对手同意后对局以和棋结束。") },
            confirmButton = {
                Button(onClick = { showDrawDialog = false; viewModel.requestDraw() }) { Text("确认提议") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDrawDialog = false }) { Text("取消") }
            },
        )
    }

    LaunchedEffect(gameId) {
        viewModel.bind(gameId, startInReplayMode = startInReplayMode)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, gameId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.refreshFromServer()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(err)
        viewModel.clearError()
    }

    val gameMatches = state.game?.id == gameId
    if (state.isLoading || state.game == null || !gameMatches) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CnChessLoadingIndicator()
        }
        return
    }

    val game = state.game!!
    val boardToRender = state.replayBoard ?: game.board
    val isGameOver = game.status != ChessGameStatus.Active
    val contentBottomPadding = if (state.isReplayMode) 64.dp else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = contentBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                val resultBanner = state.resultBannerText
                if (isGameOver && resultBanner != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = when (state.isWin) {
                            true -> MaterialTheme.colorScheme.primaryContainer
                            false -> MaterialTheme.colorScheme.errorContainer
                            null -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = resultBanner,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                PlayerHeader(
                    profile = state.opponentProfile,
                    side = state.mySide.opposite(),
                    remainingMs = state.localOpponentTimeMs,
                    totalMs = state.totalOpponentTimeMs,
                    active = game.turnSide != state.mySide && !isGameOver,
                    label = "对手",
                )
                Spacer(Modifier.height(12.dp))

                BoardView(
                    board = boardToRender,
                    mySide = state.mySide,
                    selected = state.selected,
                    legalTargets = state.legalTargets,
                    lastMove = game.lastMove,
                    centerText = centerStatusText(state),
                    onTap = {
                        if (!state.isReplayMode) viewModel.onBoardTap(it)
                    },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Spacer(Modifier.height(12.dp))

                PlayerHeader(
                    profile = state.myProfile,
                    side = state.mySide,
                    remainingMs = state.localMyTimeMs,
                    totalMs = state.totalMyTimeMs,
                    active = game.turnSide == state.mySide && !isGameOver,
                    label = "我方",
                    action = {
                        if (!state.isReplayMode && !isGameOver) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                when {
                                    state.isIncomingDrawOffer -> {
                                        Button(onClick = viewModel::acceptDraw) { Text("同意和棋") }
                                        Button(onClick = viewModel::rejectDraw) { Text("拒绝和棋") }
                                    }
                                    state.isPendingDrawOffer -> {
                                        Button(onClick = {}, enabled = false) { Text("等待响应") }
                                        Button(onClick = { showResignDialog = true }) { Text("认输") }
                                    }
                                    else -> {
                                        Button(onClick = { showDrawDialog = true }) { Text("和棋") }
                                        Button(onClick = { showResignDialog = true }) { Text("认输") }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
        if (state.isReplayMode) {
            ReplayControls(
                moveText = state.replayMoveText,
                replayIndex = state.replayIndex,
                replayTotal = state.replayTotal,
                onStart = viewModel::replayToStart,
                onPrev = viewModel::replayPrev,
                onNext = viewModel::replayNext,
                onEnd = viewModel::replayToEnd,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BoardCenterText(
    text: String,
    isWarning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (isWarning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun PlayerHeader(
    profile: Profile?,
    side: Side,
    remainingMs: Long,
    totalMs: Long,
    active: Boolean,
    label: String,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val progress =
            if (totalMs <= 0L) 0f else (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (active) progress else 1f },
                modifier = Modifier.size(46.dp),
                strokeWidth = 3.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            AsyncImage(
                model = profile?.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${profile?.displayName ?: "玩家"} · ${if (side == Side.Red) "红方" else "黑方"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "$label · ${formatMs(remainingMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) action()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReplayControls(
    moveText: String,
    replayIndex: Int,
    replayTotal: Int,
    onStart: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canGoPrev = replayIndex > 0
    val canGoNext = replayIndex < replayTotal
    BottomAppBar(
        modifier = modifier,
        windowInsets = WindowInsets(0),
    ) {
        IconButton(onClick = onStart, enabled = canGoPrev, modifier = Modifier.size(56.dp)) {
            Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = "跳到开局", modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = onPrev, enabled = canGoPrev, modifier = Modifier.size(56.dp)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一步", modifier = Modifier.size(28.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = moveText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(56.dp)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一步", modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = onEnd, enabled = canGoNext, modifier = Modifier.size(56.dp)) {
            Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "跳到末局", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun BoardView(
    board: cn.verlu.cnchess.domain.chess.BoardState,
    mySide: Side,
    selected: Position?,
    legalTargets: Set<Position>,
    lastMove: cn.verlu.cnchess.domain.chess.Move?,
    centerText: String?,
    onTap: (Position) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val boardSize = minOf(maxWidth, maxHeight)
        val boardWidth = boardSize
        val boardHeight = boardWidth * (10f / 9f)
        val cell = boardWidth / 8f
        val rowStep = boardHeight / 9f
        val cellPx = with(density) { cell.toPx() }
        val rowStepPx = with(density) { rowStep.toPx() }

        Box(
            modifier = Modifier
                .size(boardWidth, boardHeight)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val col = ((offset.x / cellPx) + 0.5f).toInt().coerceIn(0, 8)
                        val row = ((offset.y / rowStepPx) + 0.5f).toInt().coerceIn(0, 9)
                        val logical = if (mySide == Side.Red) Position(row, col) else Position(9 - row, 8 - col)
                        onTap(logical)
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bg = Color(0xFFEBCB8B)
                val lineColor = Color(0xFF5B3A29)
                val markerColor = Color(0xFF5B3A29)
                drawRect(bg)
                val w = size.width
                val h = size.height
                val dx = w / 8f
                val dy = h / 9f
                for (r in 0..9) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, r * dy),
                        end = Offset(w, r * dy),
                        strokeWidth = 2f,
                    )
                }
                for (c in 0..8) {
                    val x = c * dx
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, 4 * dy),
                        strokeWidth = 2f,
                    )
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 5 * dy),
                        end = Offset(x, h),
                        strokeWidth = 2f,
                    )
                }
                // 九宫斜线
                fun logicalCornerToOffset(logicalRow: Int, logicalCol: Int): Offset {
                    val (dr, dc) = logicalToDisplayGrid(Position(logicalRow, logicalCol), mySide)
                    return Offset(dc * dx, dr * dy)
                }
                val palaceStroke = 2f
                drawLine(lineColor, logicalCornerToOffset(0, 3), logicalCornerToOffset(2, 5), palaceStroke)
                drawLine(lineColor, logicalCornerToOffset(0, 5), logicalCornerToOffset(2, 3), palaceStroke)
                drawLine(lineColor, logicalCornerToOffset(7, 3), logicalCornerToOffset(9, 5), palaceStroke)
                drawLine(lineColor, logicalCornerToOffset(7, 5), logicalCornerToOffset(9, 3), palaceStroke)

                // Legal move rings
                val ringR = min(dx, dy) * 0.16f
                val ringStroke = Stroke(width = min(dx, dy) * 0.045f)
                legalTargets.forEach { pos ->
                    if (board.at(pos) != null) return@forEach
                    val (dr, dc) = logicalToDisplayGrid(pos, mySide)
                    val cx = dc * dx
                    val cy = dr * dy
                    drawCircle(color = markerColor, radius = ringR, center = Offset(cx, cy), style = ringStroke)
                }
            }

            for (row in 0..9) {
                for (col in 0..8) {
                    val logical = if (mySide == Side.Red) Position(row, col) else Position(9 - row, 8 - col)
                    val piece = board.at(logical) ?: continue
                    val left = cell * col
                    val top = rowStep * row
                    val selectedHere = selected == logical
                    val inLast = lastMove?.from == logical || lastMove?.to == logical
                    val highlight = selectedHere || inLast
                    val legalDestination = legalTargets.contains(logical)
                    val pieceFrac = 0.92f
                    val halfFrac = pieceFrac / 2f
                    PieceChip(
                        piece = piece,
                        modifier = Modifier
                            .size(cell * pieceFrac)
                            .align(Alignment.TopStart)
                            .offset(x = left - (cell * halfFrac), y = top - (rowStep * halfFrac)),
                        highlight = highlight,
                        legalMoveDestination = legalDestination,
                    )
                }
            }
            if (!centerText.isNullOrBlank()) {
                BoardCenterText(
                    text = centerText,
                    isWarning = centerText.contains("判和"),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun PieceChip(
    piece: Piece,
    modifier: Modifier,
    highlight: Boolean,
    legalMoveDestination: Boolean = false,
) {
    val pieceColor = if (piece.side == Side.Red) Color(0xFFB71C1C) else Color(0xFF263238)
    val legalRingColor = Color(0xFF5B3A29)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (highlight) MaterialTheme.colorScheme.tertiaryContainer else Color(0xFFF8ECD1),
                CircleShape,
            )
            .then(
                if (legalMoveDestination) Modifier.border(2.dp, legalRingColor, CircleShape)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = pieceLabel(piece),
            color = pieceColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun centerStatusText(state: GameUiState): String? {
    val game = state.game ?: return null
    val myUserId = if (state.mySide == Side.Red) game.redUserId else game.blackUserId
    val myPrefix = if (state.mySide == Side.Red) "红先" else "黑后"
    state.checkHint?.let { return it }
    if (state.isIncomingDrawOffer) return "对手请求和棋"
    if (state.isPendingDrawOffer) return "等待对手响应和棋"
    state.judgeHint?.let { return it }
    if (game.status == ChessGameStatus.Active) return "您${myPrefix} · ${state.statusText}"
    if (game.status == ChessGameStatus.Draw) return "您${myPrefix}和棋"
    if (game.status == ChessGameStatus.Resigned) {
        return when {
            game.winnerUserId == myUserId -> "对手认输，你赢了"
            game.winnerUserId != null -> "你已认输"
            else -> "认输结束"
        }
    }

    return if (game.winnerUserId != null) {
        val isWin = game.winnerUserId == myUserId
        "您${myPrefix}${if (isWin) "胜" else "负"}"
    } else {
        state.statusText
    }
}

private fun logicalToDisplayGrid(logical: Position, mySide: Side): Pair<Int, Int> =
    if (mySide == Side.Red) logical.row to logical.col
    else (9 - logical.row) to (8 - logical.col)

private fun pieceLabel(piece: Piece): String = when (piece.type) {
    PieceType.King -> if (piece.side == Side.Red) "帅" else "将"
    PieceType.Advisor -> if (piece.side == Side.Red) "仕" else "士"
    PieceType.Elephant -> if (piece.side == Side.Red) "相" else "象"
    PieceType.Horse -> "马"
    PieceType.Rook -> "车"
    PieceType.Cannon -> "炮"
    PieceType.Pawn -> if (piece.side == Side.Red) "兵" else "卒"
}

private fun formatMs(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000).toInt()
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}
