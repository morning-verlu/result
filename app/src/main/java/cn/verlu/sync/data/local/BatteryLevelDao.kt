package cn.verlu.sync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryLevelDao {
    @Query("SELECT * FROM battery_levels ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<BatteryLevelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<BatteryLevelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BatteryLevelEntity)
}
