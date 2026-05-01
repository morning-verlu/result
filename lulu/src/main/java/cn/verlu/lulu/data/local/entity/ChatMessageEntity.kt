package cn.verlu.lulu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import cn.verlu.lulu.domain.chat.ChatMessage
import cn.verlu.lulu.domain.chat.ChatMessageRole
import cn.verlu.lulu.domain.chat.ChatMemoryReference
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId"), Index("createdAt")],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val referencedMemoriesJson: String,
)

fun ChatMessageEntity.toDomain(json: Json): ChatMessage =
    ChatMessage(
        id = id,
        conversationId = conversationId,
        role = runCatching { ChatMessageRole.valueOf(role) }.getOrDefault(ChatMessageRole.Assistant),
        content = content,
        createdAt = Instant.ofEpochMilli(createdAt),
        referencedMemories = referencedMemoriesJson.toMemoryReferences(json),
    )

fun List<ChatMemoryReference>.toStorageJson(json: Json): String =
    json.encodeToString(
        ListSerializer(ChatMemoryReferenceRecord.serializer()),
        map {
            ChatMemoryReferenceRecord(
                memoryId = it.memoryId,
                title = it.title,
                excerpt = it.excerpt,
            )
        },
    )

private fun String.toMemoryReferences(json: Json): List<ChatMemoryReference> =
    runCatching {
        json.decodeFromString(
            ListSerializer(ChatMemoryReferenceRecord.serializer()),
            this,
        ).map {
            ChatMemoryReference(
                memoryId = it.memoryId,
                title = it.title,
                excerpt = it.excerpt,
            )
        }
    }.getOrDefault(emptyList())

@Serializable
private data class ChatMemoryReferenceRecord(
    @SerialName("memory_id") val memoryId: String,
    val title: String,
    val excerpt: String,
)
