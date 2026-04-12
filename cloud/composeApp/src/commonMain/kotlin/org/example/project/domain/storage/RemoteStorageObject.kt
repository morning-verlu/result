package cn.verlu.cloud.domain.storage

/**
 * 与具体厂商无关的「桶内对象」元数据（Supabase Storage / S3 / 缤纷云等可映射到此模型）。
 */
data class RemoteStorageObject(
    /** 相对路径（不含 owners/{userId}/ 前缀），例如 "photos/a.png"。 */
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val updatedAtMs: Long,
    val isDirectory: Boolean,
    val etag: String? = null,
)
