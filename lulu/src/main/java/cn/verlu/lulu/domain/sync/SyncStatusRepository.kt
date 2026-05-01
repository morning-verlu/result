package cn.verlu.lulu.domain.sync

interface SyncStatusRepository {
    suspend fun loadTodayStatus(): TodayStatus
}
