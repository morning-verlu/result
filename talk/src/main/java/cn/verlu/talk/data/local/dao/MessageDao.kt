package cn.verlu.talk.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import cn.verlu.talk.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAtMs ASC")
    fun observe(roomId: String): Flow<List<MessageEntity>>

    @Upsert
    suspend fun upsertAll(items: List<MessageEntity>)

    @Upsert
    suspend fun upsert(item: MessageEntity)

    @Query("UPDATE messages SET isDeleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getLastMessage(roomId: String): MessageEntity?

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: String)
}
