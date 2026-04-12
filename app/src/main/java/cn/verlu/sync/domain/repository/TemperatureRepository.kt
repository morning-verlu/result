package cn.verlu.sync.domain.repository

import cn.verlu.sync.domain.model.TemperatureLevel
import kotlinx.coroutines.flow.Flow

interface TemperatureRepository {
    fun observeAllTemperatures(): Flow<List<TemperatureLevel>>
    suspend fun startSync()
    suspend fun refreshFromRemote()
}
