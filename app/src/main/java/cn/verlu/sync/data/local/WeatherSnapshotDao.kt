package cn.verlu.sync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherSnapshotDao {
    @Query("SELECT * FROM weather_snapshots ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WeatherSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<WeatherSnapshotEntity>)

    @Query("SELECT * FROM weather_snapshots WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): WeatherSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WeatherSnapshotEntity)
}
