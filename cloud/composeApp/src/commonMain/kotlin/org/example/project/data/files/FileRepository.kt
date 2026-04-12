package cn.verlu.cloud.data.files

import kotlinx.coroutines.flow.Flow
import cn.verlu.cloud.domain.files.CloudFileItem

interface FileRepository {
    /** 观察本地 SQLite 缓存中当前用户的文件列表（UI 响应式驱动）。 */
    fun observeFiles(ownerId: String): Flow<List<CloudFileItem>>

    /** 从远端刷新 [relativePrefix] 下的文件列表，覆写本地缓存。 */
    suspend fun refreshFiles(ownerId: String, relativePrefix: String = ""): Result<Unit>

    /** 上传文件字节到远端并刷新本地缓存。 */
    suspend fun uploadFile(
        ownerId: String,
        relativePath: String,
        sizeBytes: Long,
        readRange: suspend (offset: Long, length: Int) -> ByteArray,
        contentType: String?,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<Unit>

    /** 删除远端对象并从本地缓存移除。 */
    suspend fun deleteFile(ownerId: String, relativePath: String): Result<Unit>

    /** 移动 / 重命名对象（同一用户命名空间下）。 */
    suspend fun moveFile(ownerId: String, from: String, to: String): Result<Unit>

    /** 获取可供浏览器直接下载的预签名 URL。 */
    suspend fun getDownloadUrl(relativePath: String, expiresInSeconds: Int = 3600): Result<String>
}
