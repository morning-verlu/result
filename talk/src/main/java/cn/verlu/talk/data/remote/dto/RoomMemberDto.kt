package cn.verlu.talk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomMemberDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
)
