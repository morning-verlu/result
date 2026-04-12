package cn.verlu.sync.data.repository

import android.util.Log
import cn.verlu.sync.data.device.DeviceLocationFetcher
import cn.verlu.sync.data.device.DeviceModelLabel
import cn.verlu.sync.data.local.WeatherSnapshotDao
import cn.verlu.sync.data.mapper.toDomain
import cn.verlu.sync.data.mapper.toEntity
import cn.verlu.sync.data.remote.WeatherSnapshotDto
import cn.verlu.sync.data.weather.QWeatherApiClient
import cn.verlu.sync.data.weather.QWeatherDailyDay
import cn.verlu.sync.di.IoDispatcher
import cn.verlu.sync.domain.model.WeatherSnapshot
import cn.verlu.sync.domain.repository.WeatherRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 远程表 `weather_snapshots` 示例（Postgres）：
 *
 * ```
 * create table public.weather_snapshots (
 *   user_id text primary key,
 *   latitude double precision not null,
 *   longitude double precision not null,
 *   city_label text not null,
 *   temp text not null,
 *   feels_like text not null default '',
 *   text_desc text not null,
 *   icon text not null,
 *   obs_time text not null default '',
 *   api_update_time text not null default '',
 *   forecast_json text not null default '',
 *   device_friendly_name text not null default '',
 *   updated_at bigint not null
 * );
 * alter table public.weather_snapshots enable row level security;
 * -- 按需添加 select/insert/update 策略（登录用户可读全员、仅改自己的行）
 * ```
 */
@Singleton
@OptIn(SupabaseExperimental::class)
class WeatherRepositoryImpl @Inject constructor(
    private val dao: WeatherSnapshotDao,
    private val supabase: SupabaseClient,
    private val qWeatherApi: QWeatherApiClient,
    private val locationFetcher: DeviceLocationFetcher,
    private val deviceModelLabel: DeviceModelLabel,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : WeatherRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val encodeJson = Json { ignoreUnknownKeys = true }

    @Volatile
    private var started = false

    override fun observeAll(): Flow<List<WeatherSnapshot>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }


    override suspend fun startSync() {
        if (started) return
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("未登录，无法同步天气")
        started = true
        pullAllFromRemote()
        subscribeRemoteChanges()
        // 首次进入由界面在拿到定位权限后调用 refreshWithCurrentLocation
    }

    override suspend fun refreshFromRemote() {
        supabase.auth.currentUserOrNull()?.id ?: error("未登录，无法同步天气")
        pullAllFromRemote()
    }

    override suspend fun refreshWithCurrentLocation() {
        val labelled = locationFetcher.fetchCurrentLabelledLocation()
        refreshWithLocation(labelled.latitude, labelled.longitude, labelled.cityLabel)
    }

    override suspend fun getByUserId(userId: String): WeatherSnapshot? =
        dao.getByUserId(userId)?.toDomain()

    override suspend fun refreshWithLocation(lat: Double, lon: Double, cityLabel: String) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("未登录，无法同步天气")

        // 调用 Edge Function 获取天气
        val weatherRes = qWeatherApi.fetchWeather(lat, lon)

        val nowResp = weatherRes.now
        val dailyResp = weatherRes.daily

        val now = nowResp.now ?: error("实况数据为空")
        val forecastJson = encodeJson.encodeToString(
            ListSerializer(QWeatherDailyDay.serializer()),
            dailyResp.daily
        )
        val t = now.temp ?: "--"
        val dto = WeatherSnapshotDto(
            userId = userId,
            latitude = lat,
            longitude = lon,
            cityLabel = cityLabel,
            temp = t,
            feelsLike = now.feelsLike ?: "",
            textDesc = now.text ?: "",
            icon = now.icon ?: "",
            obsTime = now.obsTime ?: "",
            apiUpdateTime = nowResp.updateTime ?: "",
            forecastJson = forecastJson,
            deviceFriendlyName = deviceModelLabel.getFriendlyName(),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsert(dto.toEntity())
        runCatching {
            supabase.from("weather_snapshots").upsert(dto) {
                onConflict = "user_id"
            }
        }.onFailure { e ->
            Log.e(TAG, "weather_snapshots upsert failed", e)
        }
        pullAllFromRemote()
    }

    private suspend fun pullAllFromRemote() {
        runCatching {
            val remote = supabase.from("weather_snapshots")
                .select()
                .decodeList<WeatherSnapshotDto>()
            val merged = mergeByUser(remote)
            dao.upsert(merged.map { it.toEntity() })
        }.onFailure { e ->
            Log.e(TAG, "pull weather_snapshots failed", e)
        }
    }

    private fun subscribeRemoteChanges() {
        scope.launch {
            supabase.from("weather_snapshots")
                .selectAsFlow(WeatherSnapshotDto::userId)
                .collectLatest { remoteChunk ->
                    val merged = mergeByUser(remoteChunk)
                    dao.upsert(merged.map { it.toEntity() })
                }
        }
    }

    override fun getCurrentUserId(): String? =
        supabase.auth.currentUserOrNull()?.id

    private companion object {
        private const val TAG = "WeatherSync"

        fun mergeByUser(rows: List<WeatherSnapshotDto>): List<WeatherSnapshotDto> =
            rows.groupBy { it.userId }.values.mapNotNull { g ->
                g.maxByOrNull { it.updatedAt }
            }
    }
}
