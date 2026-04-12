package cn.verlu.sync.domain.repository

import cn.verlu.sync.domain.model.BatteryLevel
import kotlinx.coroutines.flow.Flow

interface BatteryRepository {
    fun observeAllBatteries(): Flow<List<BatteryLevel>>
    suspend fun startSync()
    suspend fun refreshFromRemote()
}
