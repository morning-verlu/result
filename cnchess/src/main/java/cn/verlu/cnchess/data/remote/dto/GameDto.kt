package cn.verlu.cnchess.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameDto(
    val id: String,
    @SerialName("red_user_id") val redUserId: String,
    @SerialName("black_user_id") val blackUserId: String,
    @SerialName("turn_side") val turnSide: String = "red",
    val fen: String,
    @SerialName("red_time_ms") val redTimeMs: Long = 600_000,
    @SerialName("black_time_ms") val blackTimeMs: Long = 600_000,
    val status: String = "active",
    @SerialName("winner_user_id") val winnerUserId: String? = null,
    @SerialName("move_no") val moveNo: Long = 0,
    @SerialName("last_move_at") val lastMoveAt: String? = null,
    @SerialName("draw_reason") val drawReason: String? = null,
    @SerialName("draw_offer_by_user_id") val drawOfferByUserId: String? = null,
    @SerialName("draw_offer_at") val drawOfferAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class MoveDto(
    val id: String,
    @SerialName("game_id") val gameId: String,
    @SerialName("move_no") val moveNo: Long,
    @SerialName("side") val side: String,
    @SerialName("from_row") val fromRow: Int,
    @SerialName("from_col") val fromCol: Int,
    @SerialName("to_row") val toRow: Int,
    @SerialName("to_col") val toCol: Int,
    @SerialName("fen_before") val fenBefore: String? = null,
    @SerialName("fen_after") val fenAfter: String? = null,
    @SerialName("position_hash") val positionHash: String? = null,
    @SerialName("is_check") val isCheck: Boolean = false,
    @SerialName("is_chase") val isChase: Boolean = false,
    @SerialName("judge_tag") val judgeTag: String? = null,
)
