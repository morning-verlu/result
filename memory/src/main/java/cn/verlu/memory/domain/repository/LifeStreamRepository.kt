package cn.verlu.memory.domain.repository

import cn.verlu.memory.domain.model.LifeEntry
import kotlinx.coroutines.flow.Flow

interface LifeStreamRepository {
    fun observeEntries(): Flow<List<LifeEntry>>

    suspend fun getAll(): List<LifeEntry>

    suspend fun upsert(entry: LifeEntry)

    suspend fun delete(entryId: String)

    suspend fun exportToJson(): String

    suspend fun importFromJson(json: String): Int

    suspend fun syncPendingMedia(): Int

    suspend fun syncEntry(entryId: String): Boolean
}
