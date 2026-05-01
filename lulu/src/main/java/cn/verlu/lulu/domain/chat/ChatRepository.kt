package cn.verlu.lulu.domain.chat

import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeConversations(): Flow<List<ChatConversation>>

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>

    suspend fun startConversation(): String

    suspend fun sendMessage(
        conversationId: String?,
        content: String,
    ): String

    suspend fun saveAssistantMessageAsMemory(messageId: String)
}
