package cn.verlu.lulu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cn.verlu.lulu.domain.chat.ChatConversation
import java.time.Instant

@Entity(
    tableName = "chat_conversations",
    indices = [Index("updatedAt")],
)
data class ChatConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val lastMessagePreview: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val memoryContextCount: Int,
)

fun ChatConversationEntity.toDomain(): ChatConversation =
    ChatConversation(
        id = id,
        title = title,
        lastMessagePreview = lastMessagePreview,
        updatedAt = Instant.ofEpochMilli(updatedAt),
        messageCount = messageCount,
        memoryContextCount = memoryContextCount,
    )
