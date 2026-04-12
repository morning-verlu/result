package cn.verlu.sync.data.repository

import android.util.Log
import cn.verlu.sync.data.device.DeviceModelLabel
import cn.verlu.sync.data.device.TemperatureMonitor
import cn.verlu.sync.data.local.TemperatureLevelDao
import cn.verlu.sync.data.mapper.toDomain
import cn.verlu.sync.data.mapper.toEntity
import cn.verlu.sync.data.remote.TemperatureLevelDto
import cn.verlu.sync.di.IoDispatcher
import cn.verlu.sync.domain.model.TemperatureLevel
import cn.verlu.sync.domain.repository.TemperatureRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
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
class TemperatureRepositoryImpl @Inject constructor(
    private val dao: TemperatureLevelDao,
    private val supabase: SupabaseClient,
    private val temperatureMonitor: TemperatureMonitor,
    private val deviceModelLabel: DeviceModelLabel,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TemperatureRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    @Volatile private var started = false

    override fun observeAllTemperatures(): Flow<List<TemperatureLevel>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun startSync() {
        if (started) return

        val userId = supabase.auth.currentUserOrNull()?.id ?: error("未登录，无法同步温度")
        pullAllFromRemote()

        started = true
        observeLocalTemperatureAndUpload(userId)
        subscribeRemoteChanges()
    }

    override suspend fun refreshFromRemote() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("未登录，无法同步温度")
        uploadCurrentTemperature(userId)
        pullAllFromRemote()
    }

    private suspend fun uploadCurrentTemperature(userId: String) {
        val temp = temperatureMonitor.getCurrentTemperature()
        if (temp == -1) return
        val now = System.currentTimeMillis()
        val dto = TemperatureLevelDto(
            userId = userId,
            temperature = temp,
            updatedAt = now,
            deviceModel = deviceModelLabel.get(),
            deviceFriendlyName = deviceModelLabel.getFriendlyName()
        )
        dao.upsert(dto.toEntity())
        runCatching {
            supabase.from("temperature_levels").upsert(dto) {
                onConflict = "user_id"
            }
        }.onFailure { e ->
            Log.e(TAG, "temperature_levels upsert failed", e)
        }
    }

    private suspend fun pullAllFromRemote() {
        val remote = supabase.from("temperature_levels")
            .select()
            .decodeList<TemperatureLevelDto>()
        val merged = mergeTemperatureRowsByUser(remote)
        dao.upsert(merged.map { it.toEntity() })
    }

    private fun observeLocalTemperatureAndUpload(userId: String) {
        scope.launch {
            temperatureMonitor.observeTemperature()
                .onEach { uploadCurrentTemperature(userId) }
                .collectLatest { }
        }
    }

    private fun subscribeRemoteChanges() {
        scope.launch {
            supabase.from("temperature_levels")
                .selectAsFlow(TemperatureLevelDto::userId)
                .collectLatest { remoteChunk ->
                    val merged = mergeTemperatureRowsByUser(remoteChunk)
                    dao.upsert(merged.map { it.toEntity() })
                }
        }
    }

    private companion object {
        private const val TAG = "TemperatureSync"

        fun mergeTemperatureRowsByUser(rows: List<TemperatureLevelDto>): List<TemperatureLevelDto> =
            rows.groupBy { it.userId }.values.mapNotNull { group -> group.maxByOrNull { it.updatedAt } }
    }
}
