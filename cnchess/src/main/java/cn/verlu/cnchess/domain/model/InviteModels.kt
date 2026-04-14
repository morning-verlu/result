package cn.verlu.cnchess.domain.model

enum class InviteStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CANCELED,
}

data class ChessInvite(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val status: InviteStatus,
    val gameId: String? = null,
    val expiresAtMs: Long? = null,
    val updatedAtMs: Long? = null,
    val fromProfile: Profile? = null,
    val toProfile: Profile? = null,
)
