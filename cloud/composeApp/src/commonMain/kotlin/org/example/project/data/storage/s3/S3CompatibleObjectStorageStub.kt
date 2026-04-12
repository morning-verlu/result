package cn.verlu.cloud.data.storage.s3

import cn.verlu.cloud.data.storage.ObjectStoragePort
import cn.verlu.cloud.domain.storage.RemoteStorageObject

/**
 * 预留：若需要客户端直接连接 S3 兼容端（AWS S3 / MinIO / 缤纷云），可在此类中实现 SigV4 签名逻辑。
 * 当前架构下优先使用 Supabase Edge Function 代理（[cn.verlu.cloud.data.storage.edge.CloudEdgeFunctionAdapter]），
 * S3 密钥保存在服务端 Secrets，客户端无需持有。
 */
class S3CompatibleObjectStorageStub : ObjectStoragePort {
    private fun stub(): Nothing =
        throw UnsupportedOperationException("S3 客户端直连尚未实现；请使用 CloudEdgeFunctionAdapter。")

    override suspend fun listObjects(ownerId: String, relativePrefix: String): Result<List<RemoteStorageObject>> =
        runCatching { stub() }

    override suspend fun getUploadUrl(path: String, contentType: String?): Result<String> =
        runCatching { stub() }

    override suspend fun uploadBytes(
        path: String,
        sizeBytes: Long,
        readRange: suspend (offset: Long, length: Int) -> ByteArray,
        contentType: String?,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)?,
    ): Result<Unit> =
        runCatching { stub() }

    override suspend fun getDownloadUrl(path: String, expiresInSeconds: Int): Result<String> =
        runCatching { stub() }

    override suspend fun deleteObjects(paths: List<String>): Result<Unit> =
        runCatching { stub() }

    override suspend fun moveObject(from: String, to: String): Result<Unit> =
        runCatching { stub() }
}
