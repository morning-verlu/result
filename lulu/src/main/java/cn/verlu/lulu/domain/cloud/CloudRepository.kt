package cn.verlu.lulu.domain.cloud

import kotlinx.coroutines.flow.Flow

interface CloudRepository {
    fun observeRecentFiles(): Flow<List<CloudFile>>

    fun observeFiles(): Flow<List<CloudFile>>

    fun observeRefreshing(): Flow<Boolean>

    fun observeError(): Flow<String?>

    fun clearError()

    suspend fun refresh()
}
