package cn.verlu.talk.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cn.verlu.talk.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY lastMessageAtMs DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Upsert
    suspend fun upsertAll(items: List<ConversationEntity>)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<ConversationEntity>) {
        deleteAll()
        upsertAll(items)
    }

    @Upsert
    suspend fun upsert(item: ConversationEntity)

    @Query(
        """UPDATE conversations SET
            lastMessageContent = :content,
            lastMessageAtMs = :atMs,
            lastMessageType = :type,
            lastMessageDeleted = :deleted
           WHERE roomId = :roomId"""
    )
    suspend fun updateLastMessage(
        roomId: String,
        content: String?,
        atMs: Long,
        type: String?,
        deleted: Boolean,
    )

    @Query("SELECT * FROM conversations WHERE roomId = :roomId LIMIT 1")
    suspend fun getById(roomId: String): ConversationEntity?
}
