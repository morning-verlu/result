package cn.verlu.memory.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import cn.verlu.memory.data.remote.SupabaseConfig
import cn.verlu.memory.domain.model.LifeEntry
import cn.verlu.memory.domain.model.LifeMedia
import cn.verlu.memory.domain.repository.LifeStreamRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.Base64
import kotlin.math.max
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Singleton
class FileLifeStreamRepository(
    private val context: Context,
    private val supabase: SupabaseClient,
) : LifeStreamRepository {
    companion object {
        private const val TAG = "MemoryCloudSync"
        private const val MAX_IMAGE_UPLOAD_BYTES = 5 * 1024 * 1024
    }
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val mutex = Mutex()
    private val file: File by lazy {
        File(context.filesDir, "life_stream.json")
    }
    private val mediaDir: File by lazy {
        File(context.filesDir, "memory-media").apply { mkdirs() }
    }
    private val httpClient = HttpClient(OkHttp)

    override suspend fun getAll(): List<LifeEntry> = mutex.withLock { readAllLocked() }

    override suspend fun upsert(entry: LifeEntry) {
        mutex.withLock {
            val normalized = withContext(Dispatchers.IO) { uploadMediaIfNeeded(entry) }
            val current = readAllLocked().toMutableList()
            val existingIndex = current.indexOfFirst { it.id == entry.id }
            if (existingIndex >= 0) {
                current[existingIndex] = normalized
            } else {
                current += normalized
            }
            writeAllLocked(current)
        }
    }

    override suspend fun delete(entryId: String) {
        mutex.withLock {
            val kept = readAllLocked().filterNot { it.id == entryId }
            writeAllLocked(kept)
        }
    }

    override suspend fun exportToJson(): String = mutex.withLock {
        json.encodeToString(readAllLocked().sortedByDescending { it.createdAtEpochMs })
    }

    override suspend fun importFromJson(json: String): Int =
        mutex.withLock {
            val imported = this.json.decodeFromString<List<LifeEntry>>(json)
            val merged = readAllLocked().associateBy { it.id }.toMutableMap()
            imported.forEach { merged[it.id] = it }
            val mergedList = merged.values.sortedByDescending { it.createdAtEpochMs }
            writeAllLocked(mergedList)
            imported.size
        }

    override suspend fun syncPendingMedia(): Int =
        mutex.withLock {
            val current = readAllLocked()
            var changed = 0
            val synced = withContext(Dispatchers.IO) {
                current.map { entry ->
                    val normalized = uploadMediaIfNeeded(entry)
                    if (normalized != entry) changed++
                    normalized
                }
            }
            if (changed > 0) {
                writeAllLocked(synced)
            }
            changed
        }

    private fun readAllLocked(): List<LifeEntry> {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        if (text.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<LifeEntry>>(text) }.getOrDefault(emptyList())
    }

    private fun writeAllLocked(items: List<LifeEntry>) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        file.writeText(json.encodeToString(items.sortedByDescending { it.createdAtEpochMs }))
    }

    private suspend fun uploadMediaIfNeeded(entry: LifeEntry): LifeEntry {
        if (entry.mediaList.isEmpty()) return entry
        val userId = supabase.auth.currentUserOrNull()?.id ?: return entry
        val accessToken = supabase.auth.currentSessionOrNull()?.accessToken ?: return entry
        val uploadedMedia = entry.mediaList.mapIndexed { index, media ->
            val stableMedia = ensureLocalMediaCopy(media) ?: media
            if (isCloudSyncedUrl(stableMedia.uri)) {
                return@mapIndexed stableMedia
            }
            val stableUri = Uri.parse(stableMedia.uri)
            val bytes = readMediaBytesForUpload(stableUri, stableMedia.mimeType) ?: return@mapIndexed stableMedia
            val ext = inferExtension(stableMedia.mimeType, stableUri)
            val objectPath = "memory/${entry.id}/${System.currentTimeMillis()}-${index}-${UUID.randomUUID()}.$ext"
            runCatching {
                uploadViaCloudEdge(
                    accessToken = accessToken,
                    objectPath = objectPath,
                    bytes = bytes,
                    mimeType = stableMedia.mimeType,
                )
                val signedUrl = createDownloadUrlViaCloudEdge(
                    accessToken = accessToken,
                    fullPath = "owners/$userId/$objectPath",
                )
                Log.d(TAG, "upload success: $objectPath")
                LifeMedia(uri = signedUrl, mimeType = stableMedia.mimeType)
            }.getOrElse {
                Log.e(TAG, "upload failed: ${stableMedia.uri}", it)
                stableMedia
            }
        }
        return entry.copy(mediaList = uploadedMedia)
    }

    private suspend fun ensureLocalMediaCopy(media: LifeMedia): LifeMedia? {
        if (media.uri.startsWith("http://") || media.uri.startsWith("https://") || media.uri.startsWith("file://")) {
            return media
        }
        val uri = Uri.parse(media.uri)
        if (uri.scheme != "content") return media
        val bytes = readBytesByUri(uri) ?: return null
        val ext = inferExtension(media.mimeType, uri)
        val localFile = File(mediaDir, "${System.currentTimeMillis()}-${UUID.randomUUID()}.$ext")
        runCatching { localFile.writeBytes(bytes) }.getOrNull() ?: return null
        return media.copy(uri = Uri.fromFile(localFile).toString())
    }

    private suspend fun readBytesByUri(uri: Uri): ByteArray? =
        runCatching {
            val input: InputStream? = when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri)
                "file" -> uri.path?.let { File(it).takeIf(File::exists)?.inputStream() }
                "http", "https" -> null
                null -> File(uri.toString()).takeIf(File::exists)?.inputStream()
                else -> null
            }
            input?.use { it.readBytes() } ?: when (uri.scheme) {
                "http", "https" -> {
                    val response = httpClient.get(uri.toString())
                    if (!response.status.isSuccess()) {
                        Log.w(TAG, "download source media failed: ${response.status.value} ${uri}")
                        null
                    } else {
                        response.body<ByteArray>()
                    }
                }
                else -> null
            }
        }.getOrNull()

    private suspend fun readMediaBytesForUpload(uri: Uri, mimeType: String?): ByteArray? {
        val rawBytes = readBytesByUri(uri) ?: return null
        if (mimeType?.startsWith("image/") != true) return rawBytes
        if (rawBytes.size <= MAX_IMAGE_UPLOAD_BYTES) return rawBytes
        val compressed = compressImageToJpegUnderLimit(uri, MAX_IMAGE_UPLOAD_BYTES)
        if (compressed != null) {
            Log.d(TAG, "image compressed: ${rawBytes.size} -> ${compressed.size} bytes")
            return compressed
        }
        Log.w(TAG, "image too large and compression failed, keep original bytes: ${rawBytes.size}")
        return rawBytes
    }

    private fun compressImageToJpegUnderLimit(uri: Uri, maxBytes: Int): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxSide = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while ((maxSide / sampleSize) > 2560) sampleSize *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } ?: return null

        var working = decoded
        val qualityCandidates = intArrayOf(92, 84, 76, 68, 60, 52, 44, 36, 28)
        repeat(5) {
            for (quality in qualityCandidates) {
                val out = ByteArrayOutputStream()
                working.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val bytes = out.toByteArray()
                if (bytes.size <= maxBytes) {
                    if (working !== decoded) working.recycle()
                    decoded.recycle()
                    return bytes
                }
            }
            val nextW = (working.width * 0.82f).toInt().coerceAtLeast(320)
            val nextH = (working.height * 0.82f).toInt().coerceAtLeast(320)
            if (nextW >= working.width || nextH >= working.height) return@repeat
            val scaled = Bitmap.createScaledBitmap(working, nextW, nextH, true)
            if (working !== decoded) working.recycle()
            working = scaled
        }
        if (working !== decoded) working.recycle()
        decoded.recycle()
        return null
    }

    private fun inferExtension(mimeType: String?, uri: Uri): String {
        val byMime = when {
            mimeType == null -> null
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            mimeType.contains("gif", ignoreCase = true) -> "gif"
            mimeType.contains("mp4", ignoreCase = true) -> "mp4"
            mimeType.contains("quicktime", ignoreCase = true) -> "mov"
            mimeType.startsWith("video/") -> "mp4"
            mimeType.startsWith("image/") -> "jpg"
            else -> null
        }
        if (byMime != null) return byMime
        val path = uri.lastPathSegment.orEmpty()
        val dot = path.lastIndexOf('.')
        return if (dot >= 0 && dot < path.length - 1) path.substring(dot + 1) else "bin"
    }

    private suspend fun uploadViaCloudEdge(
        accessToken: String,
        objectPath: String,
        bytes: ByteArray,
        mimeType: String?,
    ) {
        val directUploaded = runCatching {
            val uploadUrl = createUploadUrlViaCloudEdge(
                accessToken = accessToken,
                objectPath = objectPath,
                mimeType = mimeType,
            )
            val response = httpClient.put(uploadUrl) {
                if (!mimeType.isNullOrBlank()) {
                    contentType(ContentType.parse(mimeType))
                }
                setBody(bytes)
            }
            check(response.status.isSuccess()) {
                "直传失败: ${response.status.value} ${response.bodyAsText()}"
            }
        }
        if (directUploaded.isSuccess) return

        Log.w(TAG, "direct upload failed, fallback to edge upload: $objectPath", directUploaded.exceptionOrNull())
        val payload = buildJsonObject {
            put("action", "upload")
            put("path", objectPath)
            put("contentType", mimeType ?: ContentType.Application.OctetStream.toString())
            put("base64", Base64.getEncoder().encodeToString(bytes))
        }
        callCloudEdge(accessToken, payload.toString())
    }

    private suspend fun createUploadUrlViaCloudEdge(
        accessToken: String,
        objectPath: String,
        mimeType: String?,
    ): String {
        val payload = buildJsonObject {
            put("action", "upload-url")
            put("path", objectPath)
            put("contentType", mimeType ?: ContentType.Application.OctetStream.toString())
        }
        val raw = callCloudEdge(accessToken, payload.toString())
        return json.parseToJsonElement(raw).jsonObject["url"]?.toString()?.trim('"')
            ?: error("cloud-files 返回缺少 upload url")
    }

    private suspend fun createDownloadUrlViaCloudEdge(
        accessToken: String,
        fullPath: String,
    ): String {
        val payload = buildJsonObject {
            put("action", "download-url")
            put("path", fullPath)
            put("expiresInSeconds", 31536000)
        }
        val raw = callCloudEdge(accessToken, payload.toString())
        return json.parseToJsonElement(raw).jsonObject["url"]?.toString()?.trim('"')
            ?: error("cloud-files 返回缺少 url 字段")
    }

    private suspend fun callCloudEdge(accessToken: String, payload: String): String {
        val response = httpClient.post("${SupabaseConfig.URL}/functions/v1/cloud-files") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            error("cloud-files 调用失败: ${response.status.value} ${response.bodyAsText()}")
        }
        return response.bodyAsText()
    }

    private fun isCloudSyncedUrl(uri: String): Boolean {
        if (!(uri.startsWith("http://") || uri.startsWith("https://"))) return false
        return uri.contains("owners/") && (
            uri.contains("s3.") ||
                uri.contains("cloud-kmp") ||
                uri.contains("bitiful")
            )
    }
}
