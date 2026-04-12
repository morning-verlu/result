package cn.verlu.cloud.data.storage

import cn.verlu.cloud.domain.storage.RemoteStorageObject

/**
 * 远程对象存储抽象端口。
 *
 * 约定：所有 [path] 参数均为相对于当前用户空间的**相对路径**（不含 `owners/{ownerId}/` 前缀）。
 * 实现层负责拼接实际存储路径。换服务商（Supabase→S3→MinIO）只需替换实现类。
 */
interface ObjectStoragePort {

    /** 列出用户 [ownerId] 下、[relativePrefix] 子目录内的对象（一级平铺；含虚拟目录条目）。 */
    suspend fun listObjects(ownerId: String, relativePrefix: String = ""): Result<List<RemoteStorageObject>>

    /**
     * 获取上传预签名 URL。
     * @param path 相对路径，例如 `"photos/a.jpg"` 或 `"docs/"`
     * @param contentType MIME 类型，为 null 时使用 `application/octet-stream`
     * @return 预签名 PUT URL（有效期约 1 小时）
     */
    suspend fun getUploadUrl(path: String, contentType: String?): Result<String>

    /**
     * 上传二进制数据：内部通过 [getUploadUrl] 获取预签名 URL 后直传 S3，不经过后端中转。
     * 适用于小文件（< ~50 MB）；大文件建议实现方走分片上传。
     */
    suspend fun uploadBytes(
        path: String,
        sizeBytes: Long,
        readRange: suspend (offset: Long, length: Int) -> ByteArray,
        contentType: String?,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<Unit>

    /**
     * 获取下载预签名 URL。
     * @param path 相对路径，与 [listObjects] 返回的 [RemoteStorageObject.path] 对齐
     * @param expiresInSeconds 有效期（秒），默认 3600。S3 SigV4 最大通常为 604800（7 天）
     */
    suspend fun getDownloadUrl(path: String, expiresInSeconds: Int = 3600): Result<String>

    /** 批量删除对象（相对路径列表）。 */
    suspend fun deleteObjects(paths: List<String>): Result<Unit>

    /** 移动 / 重命名对象（S3 语义：copy→delete）。 */
    suspend fun moveObject(from: String, to: String): Result<Unit>
}
