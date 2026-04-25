package cn.verlu.memory.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LifeEntryType {
    TEXT,
    IMAGE,
    VIDEO,
}

@Serializable
data class LifeMedia(
    val uri: String,
    val mimeType: String? = null,
)

@Serializable
data class LifeEntry(
    val id: String,
    val content: String,
    val createdAtEpochMs: Long,
    val type: LifeEntryType,
    val mediaList: List<LifeMedia> = emptyList(),
)
