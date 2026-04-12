package cn.verlu.music.data.repository

import android.content.Context
import android.util.Log
import cn.verlu.music.domain.model.CatalogTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedTrack(
    val url: String,
    val lrc: String?
)

class QqApiException(
    val userMessage: String,
    val retryable: Boolean,
    cause: Throwable? = null
) : IOException(userMessage, cause)

@Singleton
class MusicCatalogRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val resolvedCache = LinkedHashMap<String, Pair<Long, ResolvedTrack>>()
    private val cacheFile = File(context.filesDir, "qq_resolve_cache.tsv")

    init {
        loadPersistentResolveCache()
    }

    fun isAvailable(): Boolean = true

    fun search(keyword: String, limit: Int = 100): List<CatalogTrack> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        val api = "$SEARCH_API$encoded"
        Log.d(TAG, "search request: keyword=$trimmed, url=$api")
        val body = requestWithRetry("search") { httpGet(api) }
        Log.d(TAG, "search response body: ${body.take(LOG_PREVIEW_LEN)}")
        return parseTracks(body).take(limit)
    }

    fun fetchDiscoverList(limit: Int = 80): List<CatalogTrack> {
        Log.d(TAG, "discover request: url=$DISCOVER_API")
        val body = requestWithRetry("discover") { httpGet(DISCOVER_API) }
        Log.d(TAG, "discover response body: ${body.take(LOG_PREVIEW_LEN)}")
        return parseTracks(body).take(limit)
    }

    fun resolveTrack(rid: String): ResolvedTrack {
        val now = System.currentTimeMillis()
        synchronized(resolvedCache) {
            val cached = resolvedCache[rid]
            if (cached != null && now - cached.first <= RESOLVE_CACHE_TTL_MS) {
                return cached.second
            }
        }
        val encodedRid = URLEncoder.encode(rid, Charsets.UTF_8.name())
        val api = "$RESOLVE_API$encodedRid&type=json&level=exhigh&lrc=true"
        Log.d(TAG, "resolve request: rid=$rid, url=$api")
        val body = requestWithRetry("resolve rid=$rid") { httpGet(api) }
        Log.d(TAG, "resolve response body: ${body.take(LOG_PREVIEW_LEN)}")
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: error("解析失败：缺少 data 字段")
        val url = data["url"]?.jsonPrimitive?.contentOrNull
            ?: error("解析失败：缺少播放地址")
        val lrc = data["lrc"]?.jsonPrimitive?.contentOrNull
        val resolved = ResolvedTrack(url = url, lrc = lrc)
        synchronized(resolvedCache) {
            resolvedCache[rid] = now to resolved
            if (resolvedCache.size > MAX_RESOLVE_CACHE) {
                resolvedCache.entries.iterator().run {
                    if (hasNext()) {
                        next()
                        remove()
                    }
                }
            }
            persistResolveCache()
        }
        return resolved
    }

    fun clearResolveCache() {
        synchronized(resolvedCache) {
            resolvedCache.clear()
            if (cacheFile.exists()) cacheFile.delete()
        }
    }

    fun resolveCacheCount(): Int = synchronized(resolvedCache) { resolvedCache.size }

    fun validatePlayableUrl(url: String): Boolean {
        return requestWithRetry("validate-url", retryCount = 2) { checkUrlReachable(url) }
    }

    private fun checkUrlReachable(url: String): Boolean {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("Range", "bytes=0-1")
            setRequestProperty("Accept", "*/*")
        }
        return conn.useConnection {
            val code = responseCode
            Log.d(TAG, "validate response: code=$code, url=$url")
            code in 200..299 || code == 206
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "*/*")
        }
        return conn.useConnection {
            val code = responseCode
            val stream = if (code in 200..299) inputStream else errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Log.d(TAG, "http response: code=$code, url=$url")
            if (code !in 200..299) throw mapHttpError(code, text)
            text
        }
    }

    private fun parseTracks(body: String): List<CatalogTrack> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { item ->
            val obj = item.jsonObject
            val rid = obj["rid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val song = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val singer = obj["artist"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val pic = obj["pic"]?.jsonPrimitive?.contentOrNull
            CatalogTrack(
                id = rid.toLongOrNull() ?: return@mapNotNull null,
                rid = rid,
                song = song,
                singer = singer,
                pic = pic
            )
        }
    }

    private fun mapHttpError(code: Int, body: String): Exception {
        val message = when (code) {
            400 -> "请求参数错误"
            401, 403 -> "接口访问受限（$code）"
            404 -> "接口不存在（404）"
            408 -> "请求超时（408）"
            429 -> "请求过于频繁（429）"
            in 500..599 -> "服务暂时不可用（$code）"
            else -> "网络请求失败（HTTP $code）"
        }
        val retryable = code == 408 || code == 429 || code in 500..599
        return QqApiException("$message ${body.take(180)}", retryable = retryable)
    }

    private fun <T> requestWithRetry(
        label: String,
        retryCount: Int = 3,
        block: () -> T
    ): T {
        var attempt = 0
        var last: Throwable? = null
        while (attempt < retryCount) {
            try {
                return block()
            } catch (t: Throwable) {
                last = t
                val retryable = t is IOException || t is SocketTimeoutException || t is UnknownHostException
                Log.w(TAG, "$label failed (attempt=${attempt + 1}/$retryCount): ${t.message}")
                if (!retryable || attempt == retryCount - 1) break
                Thread.sleep((300L * (attempt + 1)).coerceAtMost(1200L))
                attempt++
            }
        }
        throw when (last) {
            is QqApiException -> last
            is SocketTimeoutException -> QqApiException("网络超时，请稍后重试", retryable = true, cause = last)
            is UnknownHostException -> QqApiException("网络不可用，请检查连接", retryable = true, cause = last)
            is IOException -> QqApiException("网络异常，请稍后重试", retryable = true, cause = last)
            else -> QqApiException("请求失败：${last?.message ?: "未知错误"}", retryable = false, cause = last)
        }
    }

    private inline fun <T> HttpURLConnection.useConnection(block: HttpURLConnection.() -> T): T {
        return try {
            block()
        } finally {
            disconnect()
        }
    }

    private fun loadPersistentResolveCache() {
        if (!cacheFile.isFile) return
        runCatching {
            val now = System.currentTimeMillis()
            val lines = cacheFile.readLines()
            synchronized(resolvedCache) {
                resolvedCache.clear()
                for (line in lines) {
                    val p = line.split('\t')
                    if (p.size < 4) continue
                    val rid = p[0]
                    val ts = p[1].toLongOrNull() ?: continue
                    if (now - ts > RESOLVE_CACHE_TTL_MS) continue
                    val url = p[2]
                    val lrc = p[3].ifEmpty { null }
                    resolvedCache[rid] = ts to ResolvedTrack(url = url, lrc = lrc)
                    if (resolvedCache.size >= MAX_RESOLVE_CACHE) break
                }
            }
        }
    }

    private fun persistResolveCache() {
        runCatching {
            val now = System.currentTimeMillis()
            val text = buildString {
                resolvedCache.forEach { (rid, pair) ->
                    if (now - pair.first > RESOLVE_CACHE_TTL_MS) return@forEach
                    val safeLrc = (pair.second.lrc ?: "").replace('\n', ' ')
                    append(rid).append('\t')
                        .append(pair.first).append('\t')
                        .append(pair.second.url).append('\t')
                        .append(safeLrc).append('\n')
                }
            }
            cacheFile.writeText(text)
        }
    }

    private companion object {
        private const val TAG = "MusicCatalogApi"
        private const val LOG_PREVIEW_LEN = 1200
        private const val RESOLVE_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val MAX_RESOLVE_CACHE = 300
        const val SEARCH_API = "https://www.qqmp3.vip/api/songs.php?type=search&keyword="
        const val DISCOVER_API = "https://www.qqmp3.vip/api/songs.php"
        const val RESOLVE_API = "https://www.qqmp3.vip/api/kw.php?rid="
    }
}
