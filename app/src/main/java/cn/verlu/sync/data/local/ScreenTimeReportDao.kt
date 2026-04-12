package cn.verlu.sync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenTimeReportDao {
    @Query(
        "SELECT * FROM screen_time_reports WHERE period = :period ORDER BY updatedAt DESC"
    )
    fun observeByPeriod(period: String): Flow<List<ScreenTimeReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ScreenTimeReportEntity>)
}
