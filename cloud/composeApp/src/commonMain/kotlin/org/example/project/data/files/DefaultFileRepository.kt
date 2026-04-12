package cn.verlu.cloud.data.files

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import cn.verlu.cloud.data.storage.ObjectStoragePort
import cn.verlu.cloud.db.CloudDatabase
import cn.verlu.cloud.domain.files.CloudFileItem

class DefaultFileRepository(
    private val database: CloudDatabase,
    private val objectStorage: ObjectStoragePort,
) : FileRepository {

    override fun observeFiles(ownerId: String): Flow<List<CloudFileItem>> =
        database.cloudTablesQueries.selectAllFileIndex(ownerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { row ->
                    CloudFileItem(
                        id = row.id,
                        ownerId = row.owner_id,
                        path = row.path,
                        fileName = row.file_name,
                        mimeType = row.mime_type,
                        sizeBytes = row.size_bytes,
                        updatedAtMs = row.updated_at_ms,
                        isDir = row.is_dir != 0L,
                    )
                }
            }

    override suspend fun refreshFiles(ownerId: String, relativePrefix: String): Result<Unit> = runCatching {
        val objects = objectStorage.listObjects(ownerId, relativePrefix).getOrThrow()
        database.cloudTablesQueries.transaction {
            // 离线优先：只替换当前目录这一层缓存，不清空其他目录缓存。
            val existing = database.cloudTablesQueries.selectAllFileIndex(ownerId).executeAsList()
            val staleInCurrentLevel = existing.filter { row ->
                parentPrefix(row.path) == relativePrefix
            }
            staleInCurrentLevel.forEach { row ->
                database.cloudTablesQueries.deleteFileByPath(ownerId, row.path)
            }
            objects.forEach { remote ->
                database.cloudTablesQueries.replaceFileIndex(
                    id = remote.path,
                    owner_id = ownerId,
                    path = remote.path,
                    file_name = remote.name,
                    mime_type = remote.etag,
                    size_bytes = remote.sizeBytes,
                    updated_at_ms = remote.updatedAtMs,
                    is_dir = if (remote.isDirectory) 1L else 0L,
                )
            }
        }
    }

    override suspend fun uploadFile(
        ownerId: String,
        relativePath: String,
        sizeBytes: Long,
        readRange: suspend (offset: Long, length: Int) -> ByteArray,
        contentType: String?,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)?,
    ): Result<Unit> = runCatching {
        objectStorage.uploadBytes(relativePath, sizeBytes, readRange, contentType, onProgress).getOrThrow()
        // 刷新当前目录
        val prefix = relativePath.substringBeforeLast("/", "").let {
            if (it.isEmpty()) "" else "$it/"
        }
        refreshFiles(ownerId, prefix).getOrThrow()
    }

    override suspend fun deleteFile(ownerId: String, relativePath: String): Result<Unit> = runCatching {
        objectStorage.deleteObjects(listOf(relativePath)).getOrThrow()
        database.cloudTablesQueries.deleteFileByPath(ownerId, relativePath)
    }

    override suspend fun moveFile(ownerId: String, from: String, to: String): Result<Unit> = runCatching {
        objectStorage.moveObject(from, to).getOrThrow()
        database.cloudTablesQueries.deleteFileByPath(ownerId, from)
        // 刷新目标目录
        val prefix = to.substringBeforeLast("/", "").let { if (it.isEmpty()) "" else "$it/" }
        refreshFiles(ownerId, prefix).getOrThrow()
    }

    override suspend fun getDownloadUrl(relativePath: String, expiresInSeconds: Int): Result<String> =
        objectStorage.getDownloadUrl(relativePath, expiresInSeconds)

    private fun parentPrefix(path: String): String {
        val p = path.trim()
        if (p.isEmpty()) return ""
        val noTail = p.trimEnd('/')
        val parent = noTail.substringBeforeLast("/", "")
        return if (parent.isEmpty()) "" else "$parent/"
    }
}
