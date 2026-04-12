package cn.verlu.sync.data.weather

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.utils.io.InternalAPI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QWeatherApiClient @Inject constructor(
    private val supabase: SupabaseClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun requireOk(code: Any?, kind: String) {
        check(code?.toString() == "200") { "和风$kind 返回 code=$code" }
    }

    @OptIn(InternalAPI::class)
    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherEdgeResponse {
        val response = supabase.functions.invoke("weather-proxy") {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            body = json.encodeToString(buildJsonObject {
                put("latitude", latitude)
                put("longitude", longitude)
            })
        }

        val responseBody = response.bodyAsText()
        val data = json.decodeFromString<WeatherEdgeResponse>(responseBody)

        requireOk(data.now.code, "实时天气")
        requireOk(data.daily.code, "3 日预报")

        return data
    }
}