package cn.verlu.memory.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_tombstones_local")
data class TombstoneEntity(
    @PrimaryKey
    val entryId: String,
    val deletedAtEpochMs: Long,
)
