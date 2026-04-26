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
enum class SyncState {
    LOCAL_ONLY,
    SYNCING,
    SYNCED,
    ERROR,
}

@Serializable
data class LifeEntry(
    val id: String,
    val content: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val retryCount: Int = 0,
    val type: LifeEntryType,
    val mediaList: List<LifeMedia> = emptyList(),
)
