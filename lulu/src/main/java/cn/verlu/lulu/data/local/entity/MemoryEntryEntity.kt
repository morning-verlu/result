package cn.verlu.lulu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_entries",
    indices = [
        Index("ownerId"),
        Index("syncStatus"),
        Index("updatedAt"),
        Index("deletedAt"),
    ],
)
data class MemoryEntryEntity(
    @PrimaryKey
    val id: String,
    val ownerId: String?,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val type: String,
    val tags: String,
    val mood: String,
    val scene: String,
    val localOnly: Boolean,
    val syncStatus: String,
    val deletedAt: Long?,
    val lastSyncedAt: Long?,
    val lastSyncAttemptAt: Long?,
    val syncError: String,
    val mediaListJson: String = "",
)
