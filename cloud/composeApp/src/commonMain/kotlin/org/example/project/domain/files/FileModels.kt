package cn.verlu.cloud.domain.files

data class CloudFileItem(
    val id: String,
    val ownerId: String,
    val path: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val updatedAtMs: Long,
    val isDir: Boolean,
)

data class TransferTaskItem(
    val id: String,
    val ownerId: String,
    val fileId: String?,
    val remotePath: String,
    val direction: String,
    val status: String,
    val transferredBytes: Long,
    val totalBytes: Long,
    val updatedAtMs: Long,
)
