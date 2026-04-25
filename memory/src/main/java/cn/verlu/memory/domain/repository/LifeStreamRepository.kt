package cn.verlu.memory.domain.repository

import cn.verlu.memory.domain.model.LifeEntry

interface LifeStreamRepository {
    suspend fun getAll(): List<LifeEntry>

    suspend fun upsert(entry: LifeEntry)

    suspend fun delete(entryId: String)

    suspend fun exportToJson(): String

    suspend fun importFromJson(json: String): Int

    suspend fun syncPendingMedia(): Int
}
