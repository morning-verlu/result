package cn.verlu.lulu.feature.sync.domain.repository

import cn.verlu.lulu.feature.sync.domain.model.TemperatureLevel
import kotlinx.coroutines.flow.Flow

interface TemperatureRepository {
    fun observeAllTemperatures(): Flow<List<TemperatureLevel>>
    suspend fun startSync()
    suspend fun refreshFromRemote()
}
