package cn.verlu.cloud.data.storage.edge

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.ktor.util.encodeBase64
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import cn.verlu.cloud.data.remote.SupabaseConfig
import cn.verlu.cloud.data.storage.ObjectStoragePort
import cn.verlu.cloud.domain.storage.RemoteStorageObject

/**
 * 通过 Supabase Edge Function `cloud-files` 代理所有对象存储操作。
 * S3 密钥保存在 Supabase Secrets 中，客户端仅凭用户 JWT 调用，安全隔离。
 */
class CloudEdgeFunctionAdapter(
    private val supabase: SupabaseClient,
) : ObjectStoragePort {
    private companion object {
        private const val EDGE_FALLBACK_MAX_BASE64_KB = 4500
        private const val MULTIPART_THRESHOLD_BYTES = 6 * 1024 * 1024
        private const val MULTIPART_PART_SIZE_BYTES = 5 * 1024 * 1024
        private const val MULTIPART_PART_MAX_RETRIES = 3
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 180_000
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val edgeFunctionUrl = "${SupabaseConfig.URL}/functions/v1/cloud-files"

    private fun accessToken(): String =
        when (val s = supabase.auth.sessionStatus.value) {
            is SessionStatus.Authenticated -> s.session.accessToken
            else -> ""
        }

    private suspend fun callEdge(body: JsonObject): String {
        val resp = httpClient.post(edgeFunctionUrl) {
            header("Authorization", "Bearer ${accessToken()}")
            header("apikey", SupabaseConfig.ANON_KEY)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        check(resp.status.isSuccess()) {
            "cloud-files edge error ${resp.status}: ${resp.bodyAsText()}"
        }
        return resp.bodyAsText()
    }

    /* ── list ─────────────────────────────────────────────────── */

    @Serializable
    private data class StorageObjectDto(
        val path: String,
        val name: String,
        val sizeBytes: Long,
        val updatedAtMs: Long,
        val isDirectory: Boolean,
        val etag: String? = null,
    )

    @Serializable
    private data class ListResponse(val objects: List<StorageObjectDto>)

    override suspend fun listObjects(
        ownerId: String,
        relativePrefix: String,
    ): Result<List<RemoteStorageObject>> = runCatching {
        val raw = callEdge(buildJsonObject {
            put("action", "list")
            put("prefix", relativePrefix)
        })
        json.decodeFromString<ListResponse>(raw).objects.map {
            RemoteStorageObject(
                path = it.path,
                name = it.name,
                sizeBytes = it.sizeBytes,
                updatedAtMs = it.updatedAtMs,
                isDirectory = it.isDirectory,
                etag = it.etag,
            )
        }
    }

    /* ── upload-url + uploadBytes ─────────────────────────────── */

    @Serializable
    private data class UrlResponse(val url: String)

    @Serializable
    private data class MultipartInitResponse(
        val uploadId: String,
        val key: String,
    )

    override suspend fun getUploadUrl(path: String, contentType: String?): Result<String> = runCatching {
        val raw = callEdge(buildJsonObject {
            put("action", "upload-url")
            put("path", path)
            put("contentType", contentType ?: "application/octet-stream")
        })
        json.decodeFromString<UrlResponse>(raw).url
    }

    override suspend fun uploadBytes(
        path: String,
        sizeBytes: Long,
        readRange: suspend (offset: Long, length: Int) -> ByteArray,
        contentType: String?,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)?,
    ): Result<Unit> = runCatching {
        val sizeKb = sizeBytes / 1024
        println("[Upload] 开始上传: path=$path, size=${sizeKb}KB, contentType=$contentType")
        if (sizeBytes >= MULTIPART_THRESHOLD_BYTES) {
            uploadLargeBytesMultipart(path, sizeBytes, readRange, contentType, onProgress)
            return@runCatching Unit
        }
        val bytes = readRange(0L, sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        check(bytes.size.toLong() == sizeBytes) { "读取文件失败：期望 $sizeBytes 字节，实际 ${bytes.size} 字节" }

        // 优先预签名直传（绕过 Edge，直接 PUT 到 S3，支持大文件 + 真实进度）
        val direct = runCatching {
            val uploadUrl = getUploadUrl(path, contentType).getOrElse {
                println("[Upload] 获取预签名URL失败: ${it.message}")
                throw it
            }
            println("[Upload] 预签名URL获取成功，开始直传 PUT")
            val resp = putWithProgress(uploadUrl, bytes, contentType, onProgress, "直传")
            println("[Upload] 直传收到响应: ${resp.status}")
            check(resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                println("[Upload] 直传失败响应体: $body")
                "direct upload failed: ${resp.status} $body"
            }
            onProgress?.invoke(sizeBytes, sizeBytes)
            println("[Upload] 直传成功")
        }

        if (direct.isFailure) {
            println("[Upload] 直传失败: ${direct.exceptionOrNull()?.message}")
            val base64Size = (sizeBytes * 4 / 3 / 1024)
            // Edge 函数 body 上限约 6MB（base64 约 4.5MB 原始）；超出时不再回退，直接抛出原始错误
            if (base64Size > EDGE_FALLBACK_MAX_BASE64_KB) {
                println("[Upload] ✗ 文件过大（base64约${base64Size}KB），已超 Edge 函数上限，无法回退，直传错误: ${direct.exceptionOrNull()?.message}")
                throw direct.exceptionOrNull() ?: Exception("直传失败且文件过大无法使用 Edge 回退")
            }
            println("[Upload] 开始 Edge 函数回退上传, base64约${base64Size}KB (原始${sizeKb}KB)")
            val payload = buildJsonObject {
                put("action", "upload")
                put("path", path)
                put("contentType", contentType ?: "application/octet-stream")
                put("base64", bytes.encodeBase64())
            }
            runCatching {
                println("[Upload] Edge 第1次尝试...")
                callEdge(payload)
            }.recoverCatching { e ->
                println("[Upload] Edge 第1次失败: ${e.message}")
                if (e is HttpRequestTimeoutException || (e.message?.contains("stalled") == true)) {
                    println("[Upload] Edge 超时，重试第2次...")
                    callEdge(payload)
                } else {
                    throw e
                }
            }.onSuccess {
                println("[Upload] Edge 上传成功")
            }.onFailure {
                println("[Upload] Edge 上传彻底失败: ${it.message}")
            }.getOrThrow()
            onProgress?.invoke(sizeBytes, sizeBytes)
        }
        Unit
    }

    private suspend fun uploadLargeBytesMultipart(
        path: String,
        sizeBytes: Long,
        readRange: suspend (offset: Long, length: Int) -> ByteArray,
        contentType: String?,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)?,
    ) {
        val total = sizeBytes
        println("[Upload] 大文件走 multipart: size=${total / 1024}KB")
        val initRaw = callEdge(buildJsonObject {
            put("action", "multipart-init")
            put("path", path)
            put("contentType", contentType ?: "application/octet-stream")
        })
        val init = json.decodeFromString<MultipartInitResponse>(initRaw)
        val uploadId = init.uploadId
        println("[Upload] multipart init 成功: uploadId=$uploadId")
        val sentPerPart = mutableMapOf<Int, Long>()
        val partEtags = mutableListOf<Pair<Int, String>>()
        var partNumber = 1
        var offset = 0L
        try {
            while (offset < total) {
                val end = minOf(offset + MULTIPART_PART_SIZE_BYTES.toLong(), total)
                val partSize = (end - offset).toInt()
                val partBytes = readRange(offset, partSize)
                check(partBytes.size == partSize) {
                    "读取分片失败：part=$partNumber, expected=$partSize, actual=${partBytes.size}"
                }
                println("[Upload] 上传分片 part=$partNumber, size=${partSize / 1024}KB")
                val resp = uploadMultipartPartWithRetry(
                    path = path,
                    uploadId = uploadId,
                    partNumber = partNumber,
                    partBytes = partBytes,
                    contentType = contentType,
                ) { sent, _ ->
                    sentPerPart[partNumber] = sent
                    val uploaded = sentPerPart.values.sum()
                    onProgress?.invoke(uploaded, total)
                }
                check(resp.status.isSuccess()) {
                    "multipart part upload failed: ${resp.status} ${resp.bodyAsText()}"
                }
                val etag = resp.headers[HttpHeaders.ETag]?.trim()?.trim('"')
                check(!etag.isNullOrEmpty()) { "missing ETag for part $partNumber" }
                partEtags += partNumber to etag
                offset = end
                partNumber++
            }
            callEdge(buildJsonObject {
                put("action", "multipart-complete")
                put("path", path)
                put("uploadId", uploadId)
                put("parts", buildJsonArray {
                    partEtags.sortedBy { it.first }.forEach { (no, etag) ->
                        add(buildJsonObject {
                            put("partNumber", no)
                            put("eTag", etag)
                        })
                    }
                })
            })
            onProgress?.invoke(total, total)
            println("[Upload] multipart 完成: parts=${partEtags.size}")
        } catch (e: Throwable) {
            println("[Upload] multipart 失败，开始 abort: ${e.message}")
            runCatching {
                callEdge(buildJsonObject {
                    put("action", "multipart-abort")
                    put("path", path)
                    put("uploadId", uploadId)
                })
            }
            throw e
        }
    }

    private suspend fun uploadMultipartPartWithRetry(
        path: String,
        uploadId: String,
        partNumber: Int,
        partBytes: ByteArray,
        contentType: String?,
        onProgress: ((Long, Long) -> Unit)?,
    ): HttpResponse {
        var lastError: Throwable? = null
        repeat(MULTIPART_PART_MAX_RETRIES) { attemptIndex ->
            val attempt = attemptIndex + 1
            val result = runCatching {
                val partUrlRaw = callEdge(buildJsonObject {
                    put("action", "multipart-part-url")
                    put("path", path)
                    put("uploadId", uploadId)
                    put("partNumber", partNumber)
                })
                val partUrl = json.decodeFromString<UrlResponse>(partUrlRaw).url
                putWithProgress(partUrl, partBytes, contentType, onProgress, "分片$partNumber")
            }
            if (result.isSuccess) return result.getOrThrow()
            lastError = result.exceptionOrNull()
            println("[Upload] 分片$partNumber 第${attempt}次失败: ${lastError?.message}")
            if (attempt < MULTIPART_PART_MAX_RETRIES) {
                delay((attempt * 700L).coerceAtMost(2000L))
                println("[Upload] 分片$partNumber 准备重试第${attempt + 1}次")
            }
        }
        throw lastError ?: Exception("multipart part $partNumber failed")
    }

    private suspend fun putWithProgress(
        uploadUrl: String,
        bytes: ByteArray,
        contentType: String?,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)?,
        tag: String,
    ): HttpResponse {
        var lastProgressAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
        return coroutineScope {
            val upload = async {
                httpClient.put(uploadUrl) {
                    setBody(
                        ProgressByteArrayContent(bytes, contentType ?: "application/octet-stream") { sent, total ->
                            lastProgressAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
                            onProgress?.invoke(sent, total)
                            if (sent % (512 * 1024L) < 65536L || sent == total) {
                                println("[Upload] ${tag}进度: ${sent * 100L / total}% (${sent / 1024}KB / ${total / 1024}KB)")
                            }
                        },
                    )
                }
            }
            while (upload.isActive) {
                delay(1500)
                val elapsed = kotlin.time.Clock.System.now().toEpochMilliseconds() - lastProgressAt
                println("[Upload] ${tag}心跳: 距上次进度回调=${elapsed}ms, isActive=${upload.isActive}")
                if (elapsed > 12000L) {
                    upload.cancel()
                    println("[Upload] ${tag}12s无进度，判定停滞")
                    error("$tag stalled")
                }
            }
            upload.await()
        }
    }

    /* ── download-url ─────────────────────────────────────────── */

    override suspend fun getDownloadUrl(path: String, expiresInSeconds: Int): Result<String> = runCatching {
        val raw = callEdge(buildJsonObject {
            put("action", "download-url")
            put("path", path)
            put("expiresInSeconds", expiresInSeconds)
        })
        json.decodeFromString<UrlResponse>(raw).url
    }

    /* ── delete ───────────────────────────────────────────────── */

    override suspend fun deleteObjects(paths: List<String>): Result<Unit> = runCatching {
        callEdge(buildJsonObject {
            put("action", "delete")
            put("paths", buildJsonArray { paths.forEach { add(JsonPrimitive(it)) } })
        })
        Unit
    }

    /* ── move ─────────────────────────────────────────────────── */

    override suspend fun moveObject(from: String, to: String): Result<Unit> = runCatching {
        val fromEncoded = encodePathForCopyHeader(from)
        val toEncoded = encodePathForCopyHeader(to)
        callEdge(buildJsonObject {
            put("action", "move")
            // 某些对象键（如中文名）在 S3 copy-source header 中必须 URL 编码，否则会报
            // "Invalid character in header content"。
            put("from", fromEncoded)
            put("to", toEncoded)
        })
        Unit
    }

    private fun encodePathForCopyHeader(path: String): String {
        return path.split('/').joinToString("/") { it.encodeURLPathPart() }
    }

    private class ProgressByteArrayContent(
        private val bytes: ByteArray,
        private val contentTypeValue: String,
        private val onProgress: ((Long, Long) -> Unit)?,
    ) : OutgoingContent.WriteChannelContent() {
        override val contentLength: Long = bytes.size.toLong()
        override val contentType: ContentType = ContentType.parse(contentTypeValue)
        // 不在 headers 里重复设置 Content-Type，避免与 contentType 属性重复导致 S3 签名校验失败。
        override val headers: Headers = Headers.Empty

        override suspend fun writeTo(channel: ByteWriteChannel) {
            val total = bytes.size.toLong()
            var sent = 0
            val chunk = 256 * 1024
            while (sent < bytes.size) {
                val end = minOf(sent + chunk, bytes.size)
                // writeFully(src, fromIndex, toIndex)：第三个参数是结束下标，不是长度。
                channel.writeFully(bytes, sent, end)
                channel.flush()
                sent = end
                onProgress?.invoke(sent.toLong(), total)
            }
        }
    }

}
