package cn.verlu.lulu.domain.chat

import java.time.Instant

data class ChatConversation(
    val id: String,
    val title: String,
    val lastMessagePreview: String,
    val updatedAt: Instant,
    val messageCount: Int,
    val memoryContextCount: Int,
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: ChatMessageRole,
    val content: String,
    val createdAt: Instant,
    val referencedMemories: List<ChatMemoryReference> = emptyList(),
)

data class ChatMemoryReference(
    val memoryId: String,
    val title: String,
    val excerpt: String,
)

enum class ChatMessageRole {
    User,
    Assistant,
}
