package cn.verlu.sync.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class LabelledLocation(
    val latitude: Double,
    val longitude: Double,
    val cityLabel: String
)

@Singleton
class DeviceLocationFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fused: FusedLocationProviderClient
) {

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLabelledLocation(): LabelledLocation {
        val loc = awaitCurrentLocation()
        val label = withContext(Dispatchers.IO) { resolveCityLabel(loc.latitude, loc.longitude) }
        return LabelledLocation(loc.latitude, loc.longitude, label)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(): Location =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }

            // 优先尝试获取“最后已知位置”，如果它足够新鲜（如 5 分钟内），直接使用
            fused.lastLocation.addOnSuccessListener { lastL ->
                if (lastL != null) {
                    val age = System.currentTimeMillis() - lastL.time
                    if (age < 5 * 60 * 1000) { // 5 分钟内
                        cont.resume(lastL)
                        return@addOnSuccessListener
                    }
                }

                // 如果没有新鲜的缓存，再启动高精度获取
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { l: Location? ->
                        if (l != null) {
                            cont.resume(l)
                        } else {
                            if (lastL != null) {
                                cont.resume(lastL)
                            } else {
                                cont.resumeWithException(IllegalStateException("无法获取位置，请确保手机已开启定位且应用已获得权限"))
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        if (lastL != null) cont.resume(lastL)
                        else cont.resumeWithException(e)
                    }
            }.addOnFailureListener { e ->
                // fallback to getCurrentLocation if lastLocation fails
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { l: Location? ->
                        if (l != null) cont.resume(l)
                        else cont.resumeWithException(e)
                    }
                    .addOnFailureListener { cont.resumeWithException(e) }
            }
        }

    @Suppress("DEPRECATION")
    private fun resolveCityLabel(lat: Double, lon: Double): String {
        return try {
            if (!Geocoder.isPresent()) {
                return formatCoords(lat, lon)
            }
            val geocoder = Geocoder(context, Locale.getDefault())
            val list = geocoder.getFromLocation(lat, lon, 1)
            val a = list?.firstOrNull() ?: return formatCoords(lat, lon)

            val city = a.locality ?: a.adminArea ?: ""
            val district = a.subLocality ?: ""
            
            val label = when {
                city.isNotBlank() && district.isNotBlank() -> {
                    // 如果城市名包含了区县名（如某些直辖市构造），则只显一个，否则合并
                    if (city.contains(district) || district.contains(city)) {
                        district.ifBlank { city }
                    } else {
                        "$city · $district"
                    }
                }
                city.isNotBlank() -> city
                district.isNotBlank() -> district
                else -> a.adminArea ?: a.countryName ?: ""
            }

            label.takeIf { it.isNotBlank() } ?: formatCoords(lat, lon)
        } catch (_: Exception) {
            formatCoords(lat, lon)
        }
    }

    private fun formatCoords(lat: Double, lon: Double) =
        String.format(Locale.US, "%.2f°, %.2f°", lon, lat)
}
