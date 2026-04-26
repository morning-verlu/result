package cn.verlu.memory.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.verlu.memory.data.local.dao.MemoryEntryDao
import cn.verlu.memory.data.local.dao.TombstoneDao
import cn.verlu.memory.data.local.entity.MemoryEntryEntity
import cn.verlu.memory.data.local.entity.TombstoneEntity

@Database(
    entities = [MemoryEntryEntity::class, TombstoneEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryEntryDao(): MemoryEntryDao
    abstract fun tombstoneDao(): TombstoneDao
}
