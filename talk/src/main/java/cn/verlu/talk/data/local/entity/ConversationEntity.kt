package cn.verlu.talk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import cn.verlu.talk.domain.model.Conversation
import cn.verlu.talk.domain.model.Message
import cn.verlu.talk.domain.model.MessageType
import cn.verlu.talk.domain.model.Profile

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val roomId: String,
    val peerUserId: String,
    val peerDisplayName: String,
    val peerAvatarUrl: String?,
    val lastMessageContent: String?,
    val lastMessageAtMs: Long,
    val lastMessageType: String?,
    val lastMessageDeleted: Boolean,
    val unreadCount: Int,
)

fun ConversationEntity.toDomain(): Conversation = Conversation(
    roomId = roomId,
    peer = Profile(
        id = peerUserId,
        displayName = peerDisplayName,
        avatarUrl = peerAvatarUrl,
    ),
    lastMessage = if (lastMessageContent != null || lastMessageDeleted) Message(
        id = "",
        roomId = roomId,
        senderId = "",
        content = lastMessageContent ?: "",
        type = when (lastMessageType) {
            "image" -> MessageType.IMAGE
            "location" -> MessageType.LOCATION
            else -> MessageType.TEXT
        },
        createdAtMs = lastMessageAtMs,
        isDeleted = lastMessageDeleted,
    ) else null,
    unreadCount = unreadCount,
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    roomId = roomId,
    peerUserId = peer.id,
    peerDisplayName = peer.displayName,
    peerAvatarUrl = peer.avatarUrl,
    lastMessageContent = lastMessage?.content,
    lastMessageAtMs = lastMessage?.createdAtMs ?: 0L,
    lastMessageType = lastMessage?.type?.name?.lowercase(),
    lastMessageDeleted = lastMessage?.isDeleted ?: false,
    unreadCount = unreadCount,
)
