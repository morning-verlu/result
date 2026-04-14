package cn.verlu.cnchess.domain.model

import cn.verlu.cnchess.domain.chess.BoardState
import cn.verlu.cnchess.domain.chess.Move
import cn.verlu.cnchess.domain.chess.Side

enum class ChessGameStatus {
    Active,
    Finished,
    Aborted,
    Timeout,
    Resigned,
    Draw,
}

data class ChessGame(
    val id: String,
    val redUserId: String,
    val blackUserId: String,
    val turnSide: Side,
    val board: BoardState,
    val redTimeMs: Long,
    val blackTimeMs: Long,
    val status: ChessGameStatus,
    val winnerUserId: String? = null,
    val moveNo: Long = 0,
    val lastMoveAtMs: Long? = null,
    val drawReason: String? = null,
    val drawOfferByUserId: String? = null,
    val drawOfferAtMs: Long? = null,
    val myProfile: Profile? = null,
    val opponentProfile: Profile? = null,
    val lastMove: Move? = null,
    val moveHistory: List<MoveRecord> = emptyList(),
)

data class MoveRecord(
    val moveNo: Long,
    val side: Side,
    val fromRow: Int,
    val fromCol: Int,
    val toRow: Int,
    val toCol: Int,
    val fenBefore: String? = null,
    val fenAfter: String? = null,
    val positionHash: String? = null,
    val isCheck: Boolean = false,
    val isChase: Boolean = false,
    val judgeTag: String? = null,
)

data class GameHistoryItem(
    val gameId: String,
    val opponentName: String,
    val opponentAvatarUrl: String? = null,
    val startedAtMs: Long? = null,
    val updatedAtMs: Long? = null,
    val status: ChessGameStatus,
    val resultText: String,
)
