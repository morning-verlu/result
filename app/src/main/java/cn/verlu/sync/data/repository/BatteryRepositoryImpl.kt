package cn.verlu.sync.data.repository

import android.util.Log
import cn.verlu.sync.data.device.BatteryMonitor
import cn.verlu.sync.data.device.DeviceModelLabel
import cn.verlu.sync.data.local.BatteryLevelDao
import cn.verlu.sync.data.mapper.toDomain
import cn.verlu.sync.data.mapper.toEntity
import cn.verlu.sync.data.remote.BatteryLevelDto
import cn.verlu.sync.di.IoDispatcher
import cn.verlu.sync.domain.model.BatteryLevel
import cn.verlu.sync.domain.repository.BatteryRepository
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.selectAsFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Singleton
@OptIn(SupabaseExperimental::class)
class BatteryRepositoryImpl @Inject constructor(
    private val dao: BatteryLevelDao,
    private val supabase: SupabaseClient,
    private val batteryMonitor: BatteryMonitor,
    private val deviceModelLabel: DeviceModelLabel,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BatteryRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    @Volatile private var started = false

    override fun observeAllBatteries(): Flow<List<BatteryLevel>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun startSync() {
        if (started) return

        val userId = supabase.auth.currentUserOrNull()?.id ?: error("未登录，无法同步电量")
        pullAllFromRemote()

        started = true
        observeLocalBatteryAndUpload(userId)
        subscribeRemoteChanges()
    }

    override suspend fun refreshFromRemote() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("未登录，无法同步电量")
        uploadCurrentBatteryLevel(userId)
        pullAllFromRemote()
    }

    private suspend fun uploadCurrentBatteryLevel(userId: String) {
        val percent = batteryMonitor.getCurrentBatteryPercent()
        val now = System.currentTimeMillis()
        val dto = BatteryLevelDto(
            userId = userId,
            batteryPercent = percent,
            updatedAt = now,
            deviceModel = deviceModelLabel.get(),
            deviceFriendlyName = deviceModelLabel.getFriendlyName()
        )
        dao.upsert(dto.toEntity())
        runCatching {
            supabase.from("battery_levels").upsert(dto) {
                onConflict = "user_id"
            }
        }.onFailure { e ->
            Log.e(TAG, "battery_levels upsert failed", e)
        }
    }

    private suspend fun pullAllFromRemote() {
        val remote = supabase.from("battery_levels")
            .select()
            .decodeList<BatteryLevelDto>()
        val merged = mergeBatteryRowsByUser(remote)
        dao.upsert(merged.map { it.toEntity() })
    }

    private fun observeLocalBatteryAndUpload(userId: String) {
        scope.launch {
            batteryMonitor.observeBatteryPercent()
                .onEach { uploadCurrentBatteryLevel(userId) }
                .collectLatest { }
        }
    }

    private fun subscribeRemoteChanges() {
        scope.launch {
            supabase.from("battery_levels")
                .selectAsFlow(BatteryLevelDto::userId)
                .collectLatest { remoteChunk ->
                    val merged = mergeBatteryRowsByUser(remoteChunk)
                    dao.upsert(merged.map { it.toEntity() })
                }
        }
    }

    private companion object {
        private const val TAG = "BatterySync"

        fun mergeBatteryRowsByUser(rows: List<BatteryLevelDto>): List<BatteryLevelDto> =
            rows.groupBy { it.userId }.values.mapNotNull { group -> group.maxByOrNull { it.updatedAt } }
    }
}
