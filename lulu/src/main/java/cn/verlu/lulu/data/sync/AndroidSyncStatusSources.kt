package cn.verlu.lulu.data.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Process
import androidx.core.content.ContextCompat
import cn.verlu.lulu.domain.sync.DeviceTemperatureStatus
import cn.verlu.lulu.domain.sync.DeviceTemperatureStatusSource
import cn.verlu.lulu.domain.sync.ScreenTimeStatus
import cn.verlu.lulu.domain.sync.ScreenTimeStatusSource
import cn.verlu.lulu.domain.sync.WeatherStatus
import cn.verlu.lulu.domain.sync.WeatherStatusSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.utils.io.InternalAPI
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class AndroidWeatherStatusSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
) : WeatherStatusSource {
    private val json = Json { ignoreUnknownKeys = true }
    private val fusedLocation by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override suspend fun loadWeather(): WeatherStatus {
        if (!context.hasAnyPermission(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return WeatherStatus(temperatureCelsius = null, condition = "需定位权限")
        }

        val location = runCatching { withTimeoutOrNull(WEATHER_LOCATION_TIMEOUT_MS) { awaitCurrentLocation() } }.getOrNull()
            ?: return WeatherStatus(temperatureCelsius = null, condition = "定位不可用")

        return runCatching {
            val response = fetchWeather(location.latitude, location.longitude)
            WeatherStatus(
                temperatureCelsius = response.now.now?.temp?.toIntOrNull(),
                condition = response.now.now?.text ?: "实时天气",
            )
        }.getOrElse {
            WeatherStatus(temperatureCelsius = null, condition = "天气不可用")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(): android.location.Location? =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }

            fusedLocation.lastLocation
                .addOnSuccessListener { last ->
                    if (last != null && System.currentTimeMillis() - last.time < RECENT_LOCATION_MS) {
                        cont.resume(last)
                        return@addOnSuccessListener
                    }

                    fusedLocation.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                        .addOnSuccessListener { current -> cont.resume(current ?: last) }
                        .addOnFailureListener { cont.resume(last) }
                }
                .addOnFailureListener { cont.resume(null) }
        }

    @OptIn(InternalAPI::class)
    private suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherEdgeResponse {
        val response = supabase.functions.invoke("weather-proxy") {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            body = json.encodeToString(buildJsonObject {
                put("latitude", latitude)
                put("longitude", longitude)
            })
        }
        val data = json.decodeFromString<WeatherEdgeResponse>(response.bodyAsText())
        check(data.now.code == "200") { "Weather now failed: ${data.now.code}" }
        return data
    }

    private companion object {
        const val RECENT_LOCATION_MS = 5 * 60 * 1000L
        const val WEATHER_LOCATION_TIMEOUT_MS = 4_000L
    }
}

@Singleton
class AndroidScreenTimeStatusSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ScreenTimeStatusSource {
    override suspend fun loadScreenTime(): ScreenTimeStatus {
        if (!hasUsageAccess()) {
            return ScreenTimeStatus(totalMinutes = null, detail = "需使用情况权限")
        }
        val end = System.currentTimeMillis()
        val begin = startOfLocalDayMillis(end)
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val totalMillis = manager.queryAndAggregateUsageStats(begin, end)
            .values
            .sumOf { it.totalTimeInForeground }
            .coerceAtMost(end - begin)
            .coerceAtLeast(0L)
        return ScreenTimeStatus(
            totalMinutes = (totalMillis / 60_000L).toInt(),
            detail = "今天",
        )
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfLocalDayMillis(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}

@Singleton
class AndroidDeviceTemperatureStatusSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceTemperatureStatusSource {
    override suspend fun loadDeviceTemperature(): DeviceTemperatureStatus {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (tenths <= 0) {
            return DeviceTemperatureStatus(celsius = null, label = "不可用")
        }
        val celsius = tenths / 10f
        return DeviceTemperatureStatus(
            celsius = celsius,
            label = when {
                celsius >= 42f -> "偏热"
                celsius >= 37f -> "温热"
                else -> "正常"
            },
        )
    }
}

private fun Context.hasAnyPermission(vararg permissions: String): Boolean =
    permissions.any { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

@Serializable
private data class WeatherEdgeResponse(
    val now: QWeatherNowResponse,
)

@Serializable
private data class QWeatherNowResponse(
    val code: String? = null,
    val now: QWeatherNowPayload? = null,
)

@Serializable
private data class QWeatherNowPayload(
    @SerialName("temp") val temp: String? = null,
    @SerialName("text") val text: String? = null,
)
