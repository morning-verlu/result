package cn.verlu.memory.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_entries_local")
data class MemoryEntryEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncState: String,
    val retryCount: Int,
    val type: String,
    val mediaListJson: String,
)
