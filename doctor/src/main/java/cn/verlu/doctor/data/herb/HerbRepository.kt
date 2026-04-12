package cn.verlu.doctor.data.herb

import android.os.Build
import androidx.annotation.RequiresApi
import cn.verlu.doctor.data.herb.dto.ArticleDetail
import cn.verlu.doctor.data.herb.dto.ArticleMeta
import cn.verlu.doctor.data.herb.dto.ArticlePreview
import cn.verlu.doctor.data.herb.dto.ItemsTotalResponse
import cn.verlu.doctor.data.local.herb.HerbArticleCacheEntity
import cn.verlu.doctor.data.local.herb.HerbBrowseCacheEntity
import cn.verlu.doctor.data.local.herb.HerbDao
import cn.verlu.doctor.data.local.herb.HerbFavoriteEntity
import cn.verlu.doctor.data.local.herb.HerbSearchCacheEntity
import cn.verlu.doctor.data.local.herb.HerbSpotlightEntity
import cn.verlu.doctor.data.remote.SupabaseConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/** 会话尚未恢复、尚无 JWT 时抛出；不应向用户展示为「业务错误」文案。 */
internal class HerbNoAccessTokenYet : Exception()

/**
 * 本草 API 一律经 Supabase Edge Function `herb-proxy` 转发，客户端只带用户 JWT，不存放 zyapi 密钥。
 * 网络返回写入 Room，供离线展示与快速打开。
 */
@Singleton
class HerbRepository @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
    private val supabase: SupabaseClient,
    private val herbDao: HerbDao,
) {

    private fun edgeProxyUrl(): String =
        SupabaseConfig.URL.trimEnd('/') + "/functions/v1/herb-proxy"

    private fun authHeaders(): Map<String, String> {
        val token = supabase.auth.currentSessionOrNull()?.accessToken
            ?: throw HerbNoAccessTokenYet()
        return mapOf(HttpHeaders.Authorization to "Bearer $token")
    }

    private fun HttpRequestBuilder.putHeaders(headers: Map<String, String>) {
        headers.forEach { (k, v) -> header(k, v) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun encodePathSegments(relPath: String): String =
        relPath.split('/').joinToString("/") { seg ->
            URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20")
        }

    private suspend fun <T> herbCatch(block: suspend () -> T): Result<T> =
        runCatching { block() }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { e ->
                val unauthorized = when (e) {
                    is ClientRequestException -> e.response.status == HttpStatusCode.Unauthorized
                    else -> e.message?.contains("401", ignoreCase = true) == true
                }
                Result.failure(
                    if (unauthorized) {
                        Exception("未授权：请重新登录后再试", e)
                    } else {
                        e
                    },
                )
            },
        )

    private suspend inline fun <reified T> getJson(
        herbPath: String,
        crossinline extra: HttpRequestBuilder.() -> Unit = {},
    ): Result<T> = herbCatch {
        val headers = authHeaders()
        client.get {
            url {
                takeFrom(edgeProxyUrl())
                parameters.append("p", herbPath)
            }
            putHeaders(headers)
            extra()
        }.body()
    }

    suspend fun itemsTotal(collection: String): Result<ItemsTotalResponse> =
        getJson("/items/total") {
            parameter("collection", collection)
        }

    suspend fun items(
        collection: String,
        offset: Int,
        limit: Int,
    ): Result<List<ArticleMeta>> = getJson("/items") {
        parameter("collection", collection)
        parameter("offset", offset)
        parameter("limit", limit)
    }

    suspend fun search(
        q: String,
        collection: String,
        limit: Int,
    ): Result<List<ArticleMeta>> = getJson("/search") {
        parameter("q", q)
        parameter("collection", collection)
        parameter("limit", limit)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun article(relPath: String): Result<ArticleDetail> {
        val enc = encodePathSegments(relPath)
        val r = getJson<ArticleDetail>("/items/$enc")
        r.getOrNull()?.let { a ->
            herbDao.upsertArticle(
                HerbArticleCacheEntity(
                    path = a.path,
                    detailJson = json.encodeToString(ArticleDetail.serializer(), a),
                ),
            )
        }
        return r
    }

    suspend fun getArticleCached(path: String): ArticleDetail? {
        val row = herbDao.getArticle(path) ?: return null
        return json.decodeFromString(ArticleDetail.serializer(), row.detailJson)
    }

    suspend fun previewRandom(
        n: Int,
        chars: Int,
        collection: String,
    ): Result<List<ArticlePreview>> = getJson("/preview/random") {
        parameter("n", n)
        parameter("chars", chars)
        parameter("collection", collection)
    }

    // --- Room：首页随机一篇（全部卷） ---

    suspend fun loadSpotlightFromCache(): ArticlePreview? {
        val row = herbDao.getSpotlight() ?: return null
        return json.decodeFromString(ArticlePreview.serializer(), row.previewJson)
    }

    suspend fun refreshSpotlightFromNetwork(): Result<ArticlePreview> = herbCatch {
        val list = previewRandom(n = 1, chars = 520, collection = "all").getOrThrow()
        val one = list.firstOrNull() ?: error("暂时没有可展示的条目")
        herbDao.upsertSpotlight(
            HerbSpotlightEntity(
                previewJson = json.encodeToString(ArticlePreview.serializer(), one),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        one
    }

    // --- Room：首页（旧多预览缓存，保留供其它逻辑复用） ---

    // --- Room：目录 ---

    suspend fun loadBrowseFromCache(collectionParam: String): HerbBrowseCached? {
        val row = herbDao.getBrowse(collectionParam) ?: return null
        val items = json.decodeFromString(ListSerializer(ArticleMeta.serializer()), row.itemsJson)
        return HerbBrowseCached(
            items = items,
            total = row.total,
            nextOffset = row.nextOffset,
            hasMore = row.hasMore,
        )
    }

    suspend fun syncBrowseFirstPage(
        collectionParam: String,
        pageSize: Int
    ): Result<HerbBrowseCached> = herbCatch {
        val totalR = itemsTotal(collectionParam).getOrThrow()
        val list = items(collectionParam, offset = 0, limit = pageSize).getOrThrow()
        val now = System.currentTimeMillis()
        herbDao.upsertBrowse(
            HerbBrowseCacheEntity(
                collectionParam = collectionParam,
                total = totalR.total,
                itemsJson = json.encodeToString(ListSerializer(ArticleMeta.serializer()), list),
                nextOffset = list.size,
                hasMore = list.size >= pageSize,
                updatedAt = now,
            ),
        )
        HerbBrowseCached(
            items = list,
            total = totalR.total,
            nextOffset = list.size,
            hasMore = list.size >= pageSize,
        )
    }

    suspend fun syncBrowseMore(collectionParam: String, pageSize: Int): Result<HerbBrowseCached> =
        herbCatch {
            val row = herbDao.getBrowse(collectionParam)
                ?: error("")
            val prev =
                json.decodeFromString(ListSerializer(ArticleMeta.serializer()), row.itemsJson)
            val more =
                items(collectionParam, offset = row.nextOffset, limit = pageSize).getOrThrow()
            val merged = prev + more
            val now = System.currentTimeMillis()
            herbDao.upsertBrowse(
                row.copy(
                    itemsJson = json.encodeToString(
                        ListSerializer(ArticleMeta.serializer()),
                        merged
                    ),
                    nextOffset = merged.size,
                    hasMore = more.size >= pageSize,
                    updatedAt = now,
                ),
            )
            HerbBrowseCached(
                items = merged,
                total = row.total,
                nextOffset = merged.size,
                hasMore = more.size >= pageSize,
            )
        }

    // --- Room：搜索 ---

    suspend fun searchAndPersist(
        q: String,
        collectionParam: String,
        limit: Int = 80
    ): Result<List<ArticleMeta>> =
        herbCatch {
            val list = search(q, collectionParam, limit).getOrThrow()
            val norm = q.trim().lowercase()
            herbDao.upsertSearch(
                HerbSearchCacheEntity(
                    queryNorm = norm,
                    resultsJson = json.encodeToString(
                        ListSerializer(ArticleMeta.serializer()),
                        list
                    ),
                ),
            )
            list
        }

    // --- Room：收藏 ---

    fun observeFavorites(): Flow<List<HerbFavoriteEntity>> = herbDao.observeFavorites()

    fun observeFavoritePathSet(): Flow<Set<String>> =
        herbDao.observeFavorites().map { rows -> rows.map { it.path }.toSet() }

    suspend fun toggleFavorite(
        path: String,
        title: String,
        collection: String,
        serial: Int?,
    ) {
        if (herbDao.getFavorite(path) != null) {
            herbDao.deleteFavorite(path)
        } else {
            herbDao.upsertFavorite(
                HerbFavoriteEntity(
                    path = path,
                    title = title,
                    collection = collection,
                    serial = serial,
                ),
            )
        }
    }

    suspend fun removeFavorite(path: String) {
        herbDao.deleteFavorite(path)
    }

    /** 撤销「取消收藏」：按移除前快照写回 Room */
    suspend fun restoreFavorite(entity: HerbFavoriteEntity) {
        herbDao.upsertFavorite(
            entity.copy(savedAt = System.currentTimeMillis()),
        )
    }
}
