package cn.verlu.lulu.feature.lifestream.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.verlu.lulu.feature.lifestream.data.local.dao.MemoryEntryDao
import cn.verlu.lulu.feature.lifestream.data.local.dao.TombstoneDao
import cn.verlu.lulu.feature.lifestream.data.local.entity.MemoryEntryEntity
import cn.verlu.lulu.feature.lifestream.data.local.entity.TombstoneEntity

@Database(
    entities = [MemoryEntryEntity::class, TombstoneEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryEntryDao(): MemoryEntryDao
    abstract fun tombstoneDao(): TombstoneDao
}
