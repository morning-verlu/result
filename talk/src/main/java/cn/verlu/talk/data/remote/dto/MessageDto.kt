package cn.verlu.talk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    val type: String = "text",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class NewMessageDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    val type: String = "text",
)
