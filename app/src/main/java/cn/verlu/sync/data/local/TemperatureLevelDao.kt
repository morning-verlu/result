package cn.verlu.sync.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TemperatureLevelDao {
    @Query("SELECT * FROM temperature_levels ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TemperatureLevelEntity>>

    @Upsert
    suspend fun upsert(entity: TemperatureLevelEntity)

    @Upsert
    suspend fun upsert(entities: List<TemperatureLevelEntity>)

    @Query("DELETE FROM temperature_levels")
    suspend fun deleteAll()
}
