package cn.verlu.lulu.domain.memory

import java.time.Instant

data class Memory(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val type: MemoryType,
    val tags: List<String>,
    val mood: String,
    val scene: String,
    val localOnly: Boolean,
    val syncStatus: MemorySyncStatus,
)

data class MemorySyncSummary(
    val localOnlyCount: Int = 0,
    val pendingCount: Int = 0,
    val syncedCount: Int = 0,
    val failedCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val lastError: String? = null,
) {
    val needsAttention: Boolean
        get() = failedCount > 0 || pendingCount > 0 || localOnlyCount > 0 || lastError != null
}

enum class MemoryType {
    Moment,
    Idea,
    Preference,
    Person,
}

enum class MemorySyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCED,
    FAILED,
}
