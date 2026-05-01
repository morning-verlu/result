package cn.verlu.lulu.data.cloud

import cn.verlu.lulu.data.remote.SupabaseConfig
import cn.verlu.lulu.di.ApplicationScope
import cn.verlu.lulu.di.IoDispatcher
import cn.verlu.lulu.domain.cloud.CloudFile
import cn.verlu.lulu.domain.cloud.CloudRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class EdgeFunctionCloudRepository @Inject constructor(
    private val supabase: SupabaseClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : CloudRepository {
    private val mutex = Mutex()
    private val allFiles = MutableStateFlow<List<CloudFile>>(emptyList())
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val edgeFunctionUrl = "${SupabaseConfig.URL}/functions/v1/cloud-files"

    init {
        appScope.launch {
            supabase.auth.sessionStatus.collectLatest { status ->
                when (status) {
                    is SessionStatus.Authenticated -> refresh()
                    else -> {
                        allFiles.value = emptyList()
                        refreshing.value = false
                        error.value = null
                    }
                }
            }
        }
    }

    override fun observeRecentFiles(): Flow<List<CloudFile>> =
        allFiles.map { list -> list.take(RECENT_COUNT) }

    override fun observeFiles(): Flow<List<CloudFile>> = allFiles.asStateFlow()

    override fun observeRefreshing(): Flow<Boolean> = refreshing.asStateFlow()

    override fun observeError(): Flow<String?> = error.asStateFlow()

    override fun clearError() {
        error.value = null
    }

    override suspend fun refresh() = mutex.withLock {
        withContext(ioDispatcher) {
            val token = accessToken()
            if (token.isBlank()) {
                allFiles.value = emptyList()
                refreshing.value = false
                return@withContext
            }
            refreshing.value = true
            error.value = null
            runCatching {
                val body = buildJsonObject {
                    put("action", "list")
                    put("prefix", "")
                }
                val resp = httpClient.post(edgeFunctionUrl) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("apikey", SupabaseConfig.ANON_KEY)
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
                check(resp.status.isSuccess()) {
                    "云盘列表失败 ${resp.status}: ${resp.bodyAsText()}"
                }
                val raw = resp.bodyAsText()
                val parsed = json.decodeFromString<ListResponse>(raw)
                parsed.objects
                    .asSequence()
                    .filterNot { it.isDirectory }
                    .map { dto ->
                        CloudFile(
                            id = dto.path,
                            name = dto.name.ifBlank { dto.path.substringAfterLast('/') },
                            sizeBytes = dto.sizeBytes,
                            updatedAt = Instant.ofEpochMilli(dto.updatedAtMs),
                        )
                    }
                    .sortedByDescending { it.updatedAt }
                    .toList()
            }
                .onSuccess { list ->
                    allFiles.value = list
                }
                .onFailure { e ->
                    error.value = e.message ?: "云盘加载失败"
                }
            refreshing.value = false
        }
    }

    private fun accessToken(): String =
        when (val s = supabase.auth.sessionStatus.value) {
            is SessionStatus.Authenticated -> s.session.accessToken
            else -> ""
        }

    @Serializable
    private data class StorageObjectDto(
        val path: String,
        val name: String,
        val sizeBytes: Long,
        val updatedAtMs: Long,
        val isDirectory: Boolean,
    )

    @Serializable
    private data class ListResponse(val objects: List<StorageObjectDto>)

    private companion object {
        private const val RECENT_COUNT = 5
    }
}
