package cn.verlu.talk.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import cn.verlu.talk.data.local.entity.FriendshipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendshipDao {

    @Query("SELECT * FROM friendships WHERE status = 'accepted' ORDER BY addresseeDisplayName ASC")
    fun observeFriends(): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE status = 'pending' AND addresseeId = :userId")
    fun observePending(userId: String): Flow<List<FriendshipEntity>>

    @Upsert
    suspend fun upsertAll(items: List<FriendshipEntity>)

    @Upsert
    suspend fun upsert(item: FriendshipEntity)

    @Query("DELETE FROM friendships WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM friendships WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE friendships SET status = 'accepted', roomId = :roomId WHERE id = :id")
    suspend fun markAccepted(id: String, roomId: String?)

    @Query("SELECT * FROM friendships WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FriendshipEntity?
}
