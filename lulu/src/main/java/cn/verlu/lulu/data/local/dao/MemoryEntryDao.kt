package cn.verlu.lulu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import cn.verlu.lulu.data.local.entity.MemoryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryEntryDao {
    @Query("SELECT * FROM memory_entries WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeEntries(): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun observeEntry(id: String): Flow<MemoryEntryEntity?>

    @Query("SELECT * FROM memory_entries WHERE id = :id LIMIT 1")
    suspend fun getEntry(id: String): MemoryEntryEntity?

    @Query("SELECT * FROM memory_entries")
    fun observeAllEntries(): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries")
    suspend fun getAllEntries(): List<MemoryEntryEntity>

    @Query("SELECT * FROM memory_entries WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getActiveEntries(): List<MemoryEntryEntity>

    @Query(
        """
        SELECT * FROM memory_entries
        WHERE (ownerId IS NULL OR ownerId = :ownerId)
          AND syncStatus IN (:statuses)
        ORDER BY updatedAt ASC
        """
    )
    suspend fun getEntriesNeedingSync(
        ownerId: String,
        statuses: List<String>,
    ): List<MemoryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MemoryEntryEntity)

    @Upsert
    suspend fun upsertEntry(entry: MemoryEntryEntity)

    @Upsert
    suspend fun upsertEntries(entries: List<MemoryEntryEntity>)

    @Update
    suspend fun updateEntry(entry: MemoryEntryEntity)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Query("DELETE FROM memory_entries WHERE ownerId IS NULL AND deletedAt IS NOT NULL")
    suspend fun deleteOwnerlessTombstones()

    @Query(
        """
        UPDATE memory_entries
        SET ownerId = :ownerId,
            localOnly = 0,
            syncStatus = :pendingStatus,
            syncError = ''
        WHERE ownerId IS NULL
          AND deletedAt IS NULL
        """
    )
    suspend fun claimOwnerlessActiveEntries(
        ownerId: String,
        pendingStatus: String,
    )
}
