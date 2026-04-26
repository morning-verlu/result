package cn.verlu.memory.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import cn.verlu.memory.data.local.entity.MemoryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryEntryDao {
    @Query("SELECT * FROM memory_entries_local ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries_local ORDER BY createdAtEpochMs DESC")
    suspend fun getAll(): List<MemoryEntryEntity>

    @Upsert
    suspend fun upsert(entity: MemoryEntryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MemoryEntryEntity>)

    @Query("DELETE FROM memory_entries_local WHERE id NOT IN (:ids)")
    suspend fun deleteNotInIds(ids: List<String>)

    @Query("DELETE FROM memory_entries_local")
    suspend fun deleteAll()
}
