package cn.verlu.lulu.feature.lifestream.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import cn.verlu.lulu.feature.lifestream.data.local.entity.TombstoneEntity

@Dao
interface TombstoneDao {
    @Query("SELECT * FROM memory_tombstones_local")
    suspend fun getAll(): List<TombstoneEntity>

    @Upsert
    suspend fun upsert(entity: TombstoneEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TombstoneEntity>)

    @Query("DELETE FROM memory_tombstones_local")
    suspend fun deleteAll()
}
