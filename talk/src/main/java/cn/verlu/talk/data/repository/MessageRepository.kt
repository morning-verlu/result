package cn.verlu.talk.data.repository

import cn.verlu.talk.domain.model.Conversation
import cn.verlu.talk.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    /** Live stream of conversations from Room cache (instant on app start). */
    fun observeConversations(): Flow<List<Conversation>>

    /** Live stream of messages in a room from Room cache. */
    fun observeMessages(roomId: String): Flow<List<Message>>

    /** Fetch from Supabase and write to Room cache. */
    suspend fun refreshConversations()

    /** Fetch from Supabase and write to Room cache. */
    suspend fun refreshMessages(roomId: String)

    /** Subscribe to realtime events, writing incoming messages to Room. */
    suspend fun subscribeToRoomMessages(
        roomId: String,
        onError: (Throwable) -> Unit = {},
    )

    suspend fun unsubscribeFromRoom(roomId: String)

    /** Subscribe to realtime events for the conversation list. */
    suspend fun subscribeToConversationUpdates(onUpdate: () -> Unit)
    suspend fun unsubscribeFromConversationUpdates()

    /** With the peer's user ID, find the room_id from local cache. Returns null if not found. */
    suspend fun findDirectRoomIdForPeer(peerUserId: String): String?

    suspend fun sendMessage(roomId: String, content: String, type: String = "text")
    suspend fun softDeleteMessage(messageId: String)
    suspend fun markAllRead(roomId: String)
}
