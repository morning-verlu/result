package cn.verlu.lulu.domain.memory

import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun observeEntries(): Flow<List<Memory>>

    fun observeEntry(id: String): Flow<Memory?>

    fun observeSyncSummary(): Flow<MemorySyncSummary>

    suspend fun createEntry(
        title: String,
        content: String,
        type: MemoryType,
        tags: List<String>,
        mood: String,
        scene: String,
    )

    suspend fun updateEntry(
        id: String,
        title: String,
        content: String,
        type: MemoryType,
        tags: List<String>,
        mood: String,
        scene: String,
    )

    suspend fun deleteEntry(id: String)

    suspend fun syncNow(): MemorySyncSummary

    suspend fun retryFailed(): MemorySyncSummary

    suspend fun exportEntriesJson(): String

    suspend fun importEntriesJson(json: String): MemorySyncSummary
}
