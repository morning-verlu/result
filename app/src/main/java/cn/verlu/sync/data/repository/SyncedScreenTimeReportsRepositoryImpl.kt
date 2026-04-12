package cn.verlu.sync.data.repository

import cn.verlu.sync.data.local.ScreenTimeReportDao
import cn.verlu.sync.data.mapper.toDomain
import cn.verlu.sync.data.mapper.toEntity
import cn.verlu.sync.data.remote.ScreenTimeReportDto
import cn.verlu.sync.di.IoDispatcher
import cn.verlu.sync.domain.model.ScreenTimePeriod
import cn.verlu.sync.domain.model.SyncedScreenTimeReport
import cn.verlu.sync.domain.repository.SyncedScreenTimeReportsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.selectAsFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

@Singleton
class SyncedScreenTimeReportsRepositoryImpl @Inject constructor(
    private val dao: ScreenTimeReportDao,
    private val supabase: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SyncedScreenTimeReportsRepository {

    override fun observeByPeriod(period: ScreenTimePeriod): Flow<List<SyncedScreenTimeReport>> {
        ensureRemoteSubscribed(period)
        return dao.observeByPeriod(period.toRemotePeriod()).map { list ->
            dedupeReports(list.map { it.toDomain() })
        }
    }

    override suspend fun refreshFromRemote(period: ScreenTimePeriod) = withContext(ioDispatcher) {
        if (supabase.auth.currentUserOrNull() == null) return@withContext
        val periodStr = period.toRemotePeriod()
        val rows = supabase.from("screen_time_reports")
            .select()
            .decodeList<ScreenTimeReportDto>()
            .filter { it.period == periodStr }
        val merged = mergeRemoteRows(rows)
        dao.upsertAll(merged.map { it.toEntity() })
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val subscribedPeriods = mutableSetOf<String>()

    @OptIn(SupabaseExperimental::class)
    private fun ensureRemoteSubscribed(period: ScreenTimePeriod) {
        val periodStr = period.toRemotePeriod()
        synchronized(subscribedPeriods) {
            if (subscribedPeriods.contains(periodStr)) return
            subscribedPeriods.add(periodStr)
        }

        scope.launch {
            if (supabase.auth.currentUserOrNull() == null) return@launch
            // Realtime 推送会触发 collect；我们只把对应 period 的行 upsert 到 Room
            supabase.from("screen_time_reports")
                .selectAsFlow(
                    listOf(ScreenTimeReportDto::userId, ScreenTimeReportDto::period)
                )
                .collectLatest { remoteRows ->
                    val filtered = remoteRows.filter { it.period == periodStr }
                    val merged = mergeRemoteRows(filtered)
                    dao.upsertAll(merged.map { it.toEntity() })
                }
        }
    }

    private fun ScreenTimePeriod.toRemotePeriod(): String = when (this) {
        ScreenTimePeriod.Today -> "today"
        ScreenTimePeriod.Last7Days -> "last_7_days"
    }

    private companion object {
        fun dedupeReports(reports: List<SyncedScreenTimeReport>): List<SyncedScreenTimeReport> =
            reports
                .groupBy { "${it.userId}|${it.period}" }
                .values
                .mapNotNull { group -> group.maxByOrNull { it.updatedAtMillis } }
                .sortedByDescending { it.updatedAtMillis }

        fun mergeRemoteRows(rows: List<ScreenTimeReportDto>): List<ScreenTimeReportDto> =
            rows
                .groupBy { "${it.userId}|${it.period}" }
                .values
                .mapNotNull { group -> group.maxByOrNull { it.updatedAt } }
    }
}
