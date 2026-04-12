package cn.verlu.doctor.data.herb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class ArticleMeta(
    val path: String,
    val collection: String,
    @JsonNames("serial", "no", "index", "num", "ordinal")
    val serial: Int? = null,
    val title: String,
    @SerialName("size_bytes") val sizeBytes: Int = 0,
    val mtime: Double = 0.0,
)

@Serializable
data class ArticleDetail(
    val path: String,
    val collection: String,
    @JsonNames("serial", "no", "index", "num", "ordinal")
    val serial: Int? = null,
    val title: String,
    @SerialName("size_bytes") val sizeBytes: Int = 0,
    val mtime: Double = 0.0,
    val content: String = "",
)

@Serializable
data class ArticlePreview(
    val path: String,
    val collection: String,
    @JsonNames("serial", "no", "index", "num", "ordinal")
    val serial: Int? = null,
    val title: String,
    @SerialName("size_bytes") val sizeBytes: Int = 0,
    val mtime: Double = 0.0,
    val preview: String = "",
    @SerialName("preview_truncated") val previewTruncated: Boolean = false,
    @SerialName("content_chars") val contentChars: Int = 0,
)

@Serializable
data class HealthResponse(
    val indexed: Int = 0,
)

@Serializable
data class ItemsTotalResponse(
    val collection: String = "",
    val total: Int = 0,
)

/** UI 用：仅使用接口 [serial]；有则 `No.n`，无则短提示。 */
fun formatArticleSerialLine(serial: Int?): String =
    serial?.let { "No.$it" } ?: "序号未提供"
