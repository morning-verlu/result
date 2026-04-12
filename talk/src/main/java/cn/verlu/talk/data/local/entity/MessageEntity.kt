package cn.verlu.talk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cn.verlu.talk.domain.model.Message
import cn.verlu.talk.domain.model.MessageType
import cn.verlu.talk.domain.model.Profile

@Entity(
    tableName = "messages",
    indices = [Index("roomId"), Index("createdAtMs")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val senderId: String,
    val content: String,
    val type: String,
    val createdAtMs: Long,
    val isDeleted: Boolean,
    val senderDisplayName: String?,
    val senderAvatarUrl: String?,
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    roomId = roomId,
    senderId = senderId,
    content = content,
    type = when (type) {
        "image" -> MessageType.IMAGE
        "location" -> MessageType.LOCATION
        else -> MessageType.TEXT
    },
    createdAtMs = createdAtMs,
    isDeleted = isDeleted,
    senderProfile = if (senderDisplayName != null) Profile(
        id = senderId,
        displayName = senderDisplayName,
        avatarUrl = senderAvatarUrl,
    ) else null,
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    roomId = roomId,
    senderId = senderId,
    content = content,
    type = type.name.lowercase(),
    createdAtMs = createdAtMs,
    isDeleted = isDeleted,
    senderDisplayName = senderProfile?.displayName,
    senderAvatarUrl = senderProfile?.avatarUrl,
)
