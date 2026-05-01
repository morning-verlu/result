package cn.verlu.lulu.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemoryDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val content: String,
    val type: String,
    val tags: List<String> = emptyList(),
    val mood: String = "",
    val scene: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)
