package cn.verlu.cnchess.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InviteDto(
    val id: String,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id") val toUserId: String,
    val status: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("game_id") val gameId: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PresenceDto(
    @SerialName("user_id") val userId: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("is_foreground") val isForeground: Boolean = false,
)
