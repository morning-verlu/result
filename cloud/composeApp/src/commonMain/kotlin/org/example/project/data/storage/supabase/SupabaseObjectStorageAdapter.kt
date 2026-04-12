package cn.verlu.cloud.data.storage.supabase

import cn.verlu.cloud.data.remote.SupabaseConfig
import cn.verlu.cloud.data.storage.ObjectStoragePort
import cn.verlu.cloud.domain.storage.RemoteStorageObject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.FileObject
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Supabase Storage 实现的 [ObjectStoragePort]（备用；主实现为 [cn.verlu.cloud.data.storage.edge.CloudEdgeFunctionAdapter]）。
 */
class SupabaseObjectStorageAdapter(
    private val supabase: SupabaseClient,
    private val bucketId: String = SupabaseConfig.STORAGE_BUCKET,
) : ObjectStoragePort {

    private val bucket get() = supabase.storage.from(bucketId)

    override suspend fun listObjects(ownerId: String, relativePrefix: String): Result<List<RemoteStorageObject>> =
        runCatching {
            val prefix = buildPrefix(ownerId, relativePrefix)
            bucket.list(prefix = prefix).map { file -> file.toRemote(prefix) }
        }

    override suspend fun getUploadUrl(path: String, contentType: String?): Result<String> =
        Result.failure(UnsupportedOperationException("SupabaseObjectStorageAdapter 不支持预签名上传 URL；请切换到 CloudEdgeFunctionAdapter"))

    override suspend fun uploadBytes(
        path: String,
        sizeBytes: Long,
        readRange: suspend (offset: Long, length: Int) -> ByteArray,
        contentType: String?,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)?,
    ): Result<Unit> =
        runCatching {
            val bytes = readRange(0L, sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            bucket.upload(path, bytes) {
                if (contentType != null) this.contentType = ContentType.parse(contentType)
            }
            onProgress?.invoke(sizeBytes, sizeBytes)
            Unit
        }

    override suspend fun getDownloadUrl(path: String, expiresInSeconds: Int): Result<String> =
        runCatching {
            bucket.createSignedUrl(path, expiresInSeconds.seconds)
        }

    override suspend fun deleteObjects(paths: List<String>): Result<Unit> =
        runCatching {
            paths.forEach { bucket.delete(it) }
        }

    override suspend fun moveObject(from: String, to: String): Result<Unit> =
        runCatching { bucket.move(from, to) }

    private fun buildPrefix(ownerId: String, relativePrefix: String): String {
        val rel = relativePrefix.trim().trim('/')
        val base = "owners/$ownerId"
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    private fun FileObject.toRemote(listPrefix: String): RemoteStorageObject {
        val isFolder = id == null && metadata == null
        val fullPath = if (listPrefix.isEmpty()) name else "$listPrefix/$name"
        val size = metadata.longSizeOrNull() ?: 0L
        val updatedMs = updatedAt?.toEpochMilliseconds() ?: createdAt?.toEpochMilliseconds() ?: 0L
        return RemoteStorageObject(
            path = fullPath,
            name = name,
            sizeBytes = if (isFolder) 0L else size,
            updatedAtMs = updatedMs,
            isDirectory = isFolder,
        )
    }

    private fun JsonObject?.longSizeOrNull(): Long? =
        this?.get("size")?.let { el -> (el as? JsonPrimitive)?.longOrNull }
}
