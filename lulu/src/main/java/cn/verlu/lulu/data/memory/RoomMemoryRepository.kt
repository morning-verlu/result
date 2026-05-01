package cn.verlu.lulu.data.memory

import cn.verlu.lulu.data.local.dao.MemoryEntryDao
import cn.verlu.lulu.data.local.entity.MemoryEntryEntity
import cn.verlu.lulu.feature.lifestream.data.local.entity.MemoryEntryEntity as LifeStreamEntryEntity
import cn.verlu.lulu.data.remote.dto.MemoryDto
import cn.verlu.lulu.di.ApplicationScope
import cn.verlu.lulu.di.IoDispatcher
import cn.verlu.lulu.domain.memory.Memory
import cn.verlu.lulu.domain.memory.MemoryRepository
import cn.verlu.lulu.domain.memory.MemorySyncStatus
import cn.verlu.lulu.domain.memory.MemorySyncSummary
import cn.verlu.lulu.domain.memory.MemoryType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
class RoomMemoryRepository @Inject constructor(
    private val memoryEntryDao: MemoryEntryDao,
    private val lifeStreamEntryDao: cn.verlu.lulu.feature.lifestream.data.local.dao.MemoryEntryDao,
    private val supabase: SupabaseClient,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : MemoryRepository {
    private val syncMutex = Mutex()
    private val syncRuntime = MutableStateFlow(SyncRuntimeState())
    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    init {
        appScope.launch {
            importLifeStreamEntries()
        }
        appScope.launch {
            supabase.auth.sessionStatus.collectLatest { status ->
                if (status is SessionStatus.Authenticated) {
                    importLifeStreamEntries()
                    syncNow()
                }
            }
        }
    }

    override fun observeEntries(): Flow<List<Memory>> =
        combine(memoryEntryDao.observeEntries(), lifeStreamEntryDao.observeAll()) { entries, lifeStreamEntries ->
            val nativeIds = entries.mapTo(mutableSetOf()) { it.id }
            (entries.map { it.toDomain() } +
                lifeStreamEntries
                    .filter { it.id !in nativeIds }
                    .map { it.toMemory() })
                .sortedByDescending { it.createdAt }
        }

    override fun observeEntry(id: String): Flow<Memory?> =
        combine(memoryEntryDao.observeEntry(id), lifeStreamEntryDao.observeAll()) { entry, lifeStreamEntries ->
            entry?.toDomain() ?: lifeStreamEntries.firstOrNull { it.id == id }?.toMemory()
        }

    override fun observeSyncSummary(): Flow<MemorySyncSummary> =
        combine(memoryEntryDao.observeAllEntries(), syncRuntime) { entries, runtime ->
            entries.toSyncSummary(runtime)
        }

    override suspend fun createEntry(
        title: String,
        content: String,
        type: MemoryType,
        tags: List<String>,
        mood: String,
        scene: String,
    ) = withContext(dispatcher) {
        val now = Instant.now().toEpochMilli()
        val ownerId = currentUserId()
        memoryEntryDao.insertEntry(
            MemoryEntryEntity(
                id = UUID.randomUUID().toString(),
                ownerId = ownerId,
                title = title.trim(),
                content = content.trim(),
                createdAt = now,
                updatedAt = now,
                type = type.name,
                tags = tags.toStorageValue(),
                mood = mood.trim(),
                scene = scene.trim(),
                localOnly = ownerId == null,
                syncStatus = if (ownerId == null) {
                    MemorySyncStatus.LOCAL_ONLY.name
                } else {
                    MemorySyncStatus.PENDING.name
                },
                deletedAt = null,
                lastSyncedAt = null,
                lastSyncAttemptAt = null,
                syncError = "",
            )
        )
        scheduleSyncIfSignedIn()
    }

    override suspend fun updateEntry(
        id: String,
        title: String,
        content: String,
        type: MemoryType,
        tags: List<String>,
        mood: String,
        scene: String,
    ) = withContext(dispatcher) {
        val current = memoryEntryDao.getEntry(id) ?: return@withContext
        val now = Instant.now().toEpochMilli()
        val ownerId = current.ownerId ?: currentUserId()
        memoryEntryDao.updateEntry(
            current.copy(
                ownerId = ownerId,
                title = title.trim(),
                content = content.trim(),
                updatedAt = now,
                type = type.name,
                tags = tags.toStorageValue(),
                mood = mood.trim(),
                scene = scene.trim(),
                localOnly = ownerId == null,
                syncStatus = if (ownerId == null) {
                    MemorySyncStatus.LOCAL_ONLY.name
                } else {
                    MemorySyncStatus.PENDING.name
                },
                lastSyncAttemptAt = null,
                syncError = "",
            )
        )
        scheduleSyncIfSignedIn()
    }

    override suspend fun deleteEntry(id: String) = withContext(dispatcher) {
        val current = memoryEntryDao.getEntry(id) ?: return@withContext
        val now = Instant.now().toEpochMilli()
        val ownerId = current.ownerId ?: currentUserId()
        if (ownerId == null && current.syncStatus == MemorySyncStatus.LOCAL_ONLY.name) {
            memoryEntryDao.deleteEntry(id)
            return@withContext
        }
        memoryEntryDao.updateEntry(
            current.copy(
                ownerId = ownerId,
                updatedAt = now,
                deletedAt = now,
                localOnly = ownerId == null,
                syncStatus = if (ownerId == null) {
                    MemorySyncStatus.LOCAL_ONLY.name
                } else {
                    MemorySyncStatus.PENDING.name
                },
                lastSyncAttemptAt = null,
                syncError = "",
            )
        )
        scheduleSyncIfSignedIn()
    }

    override suspend fun syncNow(): MemorySyncSummary = withContext(dispatcher) {
        val userId = currentUserId()
        if (userId == null) {
            syncRuntime.value = SyncRuntimeState(isSyncing = false, lastError = null)
            return@withContext currentSummary()
        }

        syncMutex.withLock {
            syncRuntime.value = SyncRuntimeState(isSyncing = true, lastError = null)
            val errors = mutableListOf<String>()
            runCatching {
                memoryEntryDao.deleteOwnerlessTombstones()
                memoryEntryDao.claimOwnerlessActiveEntries(
                    ownerId = userId,
                    pendingStatus = MemorySyncStatus.PENDING.name,
                )
                pushDirtyEntries(userId = userId, errors = errors)
                pullRemoteEntries(userId = userId, errors = errors)
                pullLegacyLifeStreamEntries(userId = userId, errors = errors)
            }.onFailure { throwable ->
                errors += throwable.safeMessage()
            }
            syncRuntime.value = SyncRuntimeState(
                isSyncing = false,
                lastError = errors.distinct().joinToString("; ").takeIf { it.isNotBlank() },
            )
            currentSummary()
        }
    }

    override suspend fun retryFailed(): MemorySyncSummary = withContext(dispatcher) {
        val now = Instant.now().toEpochMilli()
        val failed = memoryEntryDao.getAllEntries()
            .filter { it.syncStatus == MemorySyncStatus.FAILED.name }
            .map {
                it.copy(
                    syncStatus = MemorySyncStatus.PENDING.name,
                    updatedAt = maxOf(it.updatedAt, now),
                    syncError = "",
                )
            }
        if (failed.isNotEmpty()) {
            memoryEntryDao.upsertEntries(failed)
        }
        syncNow()
    }

    override suspend fun exportEntriesJson(): String = withContext(dispatcher) {
        val export = MemoryExportFile(
            exportedAt = Instant.now().toString(),
            memories = memoryEntryDao.getActiveEntries().map { it.toExportItem() },
        )
        exportJson.encodeToString(MemoryExportFile.serializer(), export)
    }

    override suspend fun importEntriesJson(json: String): MemorySyncSummary = withContext(dispatcher) {
        val export = exportJson.decodeFromString(MemoryExportFile.serializer(), json)
        val ownerId = currentUserId()
        val now = Instant.now().toEpochMilli()
        val entries = export.memories.map { item ->
            val createdAt = item.createdAt.toEpochMilliOr(now)
            val updatedAt = item.updatedAt.toEpochMilliOr(createdAt)
            MemoryEntryEntity(
                id = item.id,
                ownerId = ownerId,
                title = item.title.ifBlank {
                    item.content.lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        ?.trim()
                        ?.take(32)
                        ?: "时间线记录"
                },
                content = item.content,
                createdAt = createdAt,
                updatedAt = updatedAt,
                type = item.type.ifBlank { MemoryType.Moment.name },
                tags = item.tags.toStorageValue(),
                mood = item.mood,
                scene = item.scene,
                localOnly = ownerId == null,
                syncStatus = if (ownerId == null) {
                    MemorySyncStatus.LOCAL_ONLY.name
                } else {
                    MemorySyncStatus.PENDING.name
                },
                deletedAt = null,
                lastSyncedAt = null,
                lastSyncAttemptAt = null,
                syncError = "",
            )
        }
        if (entries.isNotEmpty()) {
            memoryEntryDao.upsertEntries(entries)
            scheduleSyncIfSignedIn()
        }
        currentSummary()
    }

    private suspend fun importLifeStreamEntries() = withContext(dispatcher) {
        val legacyEntries = lifeStreamEntryDao.getAll()
        if (legacyEntries.isEmpty()) return@withContext

        val existingIds = memoryEntryDao.getAllEntries().mapTo(mutableSetOf()) { it.id }
        val ownerId = currentUserId()
        val imports = legacyEntries
            .filter { it.id !in existingIds }
            .map { entry ->
                val content = entry.content.trim()
                val title = content
                    .lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?.take(32)
                    ?: "时间线记录"
                MemoryEntryEntity(
                    id = entry.id,
                    ownerId = ownerId,
                    title = title,
                    content = content,
                    createdAt = entry.createdAtEpochMs,
                    updatedAt = entry.updatedAtEpochMs,
                    type = MemoryType.Moment.name,
                    tags = "",
                    mood = "",
                    scene = "",
                    localOnly = ownerId == null,
                    syncStatus = when {
                        ownerId == null -> MemorySyncStatus.LOCAL_ONLY.name
                        entry.syncState == "SYNCED" -> MemorySyncStatus.SYNCED.name
                        else -> MemorySyncStatus.PENDING.name
                    },
                    deletedAt = null,
                    lastSyncedAt = if (entry.syncState == "SYNCED") entry.updatedAtEpochMs else null,
                    lastSyncAttemptAt = null,
                    syncError = "",
                )
            }

        if (imports.isNotEmpty()) {
            memoryEntryDao.upsertEntries(imports)
        }
    }

    private suspend fun pushDirtyEntries(
        userId: String,
        errors: MutableList<String>,
    ) {
        val dirtyEntries = memoryEntryDao.getEntriesNeedingSync(
            ownerId = userId,
            statuses = listOf(
                MemorySyncStatus.LOCAL_ONLY.name,
                MemorySyncStatus.PENDING.name,
                MemorySyncStatus.FAILED.name,
            ),
        )

        dirtyEntries.forEach { entry ->
            val attemptAt = Instant.now().toEpochMilli()
            val pending = entry.copy(
                ownerId = userId,
                localOnly = false,
                syncStatus = MemorySyncStatus.PENDING.name,
                lastSyncAttemptAt = attemptAt,
                syncError = "",
            )
            memoryEntryDao.upsertEntry(pending)
            runCatching {
                supabase.postgrest["memories"].upsert(pending.toRemoteDto(userId))
                memoryEntryDao.upsertEntry(
                    pending.copy(
                        syncStatus = MemorySyncStatus.SYNCED.name,
                        lastSyncedAt = Instant.now().toEpochMilli(),
                        syncError = "",
                    )
                )
            }.onFailure { throwable ->
                val message = throwable.safeMessage()
                errors += message
                memoryEntryDao.upsertEntry(
                    pending.copy(
                        syncStatus = MemorySyncStatus.FAILED.name,
                        syncError = message,
                    )
                )
            }
        }
    }

    private suspend fun pullRemoteEntries(
        userId: String,
        errors: MutableList<String>,
    ) {
        runCatching {
            supabase.postgrest["memories"].select {
                filter { eq("user_id", userId) }
                order("updated_at", Order.DESCENDING)
            }.decodeList<MemoryDto>()
        }.onSuccess { rows ->
            mergeRemoteRows(userId = userId, rows = rows)
        }.onFailure { throwable ->
            errors += throwable.safeMessage()
        }
    }

    private suspend fun pullLegacyLifeStreamEntries(
        userId: String,
        errors: MutableList<String>,
    ) {
        runCatching {
            supabase.postgrest["memory_entries"].select {
                filter { eq("user_id", userId) }
                order("created_at_epoch_ms", Order.DESCENDING)
            }.decodeList<LegacyLifeStreamRow>()
        }.onSuccess { rows ->
            val now = Instant.now().toEpochMilli()
            rows
                .filterNot { it.isDeleted }
                .forEach { row ->
                    if (memoryEntryDao.getEntry(row.entryId) != null) return@forEach
                    memoryEntryDao.upsertEntry(row.toMemoryEntity(userId = userId, lastSyncedAt = now))
                }
        }.onFailure { throwable ->
            errors += throwable.safeMessage()
        }
    }

    private suspend fun mergeRemoteRows(
        userId: String,
        rows: List<MemoryDto>,
    ) {
        val now = Instant.now().toEpochMilli()
        rows.forEach { row ->
            val remote = row.toEntity(ownerId = userId, lastSyncedAt = now)
            val local = memoryEntryDao.getEntry(row.id)
            when {
                local == null && remote.deletedAt == null -> memoryEntryDao.upsertEntry(remote)
                local == null -> Unit
                local.syncStatus != MemorySyncStatus.SYNCED.name &&
                    local.updatedAt < remote.updatedAt &&
                    local.deletedAt == null -> {
                    memoryEntryDao.upsertEntry(local.toConflictCopy(userId = userId, now = now))
                    memoryEntryDao.upsertEntry(remote)
                }
                local.syncStatus != MemorySyncStatus.SYNCED.name &&
                    local.updatedAt > remote.updatedAt -> Unit
                remote.updatedAt >= local.updatedAt -> memoryEntryDao.upsertEntry(remote)
            }
        }
    }

    private fun scheduleSyncIfSignedIn() {
        if (currentUserId() == null) return
        appScope.launch { syncNow() }
    }

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    private suspend fun currentSummary(): MemorySyncSummary =
        memoryEntryDao.getAllEntries().toSyncSummary(syncRuntime.value)

    private fun MemoryEntryEntity.toDomain(): Memory =
        Memory(
            id = id,
            title = title,
            content = content,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            type = runCatching { MemoryType.valueOf(type) }.getOrDefault(MemoryType.Moment),
            tags = tags.fromStorageValue(),
            mood = mood,
            scene = scene,
            localOnly = localOnly,
            syncStatus = runCatching {
                MemorySyncStatus.valueOf(syncStatus)
            }.getOrDefault(MemorySyncStatus.LOCAL_ONLY),
        )

    private fun LifeStreamEntryEntity.toMemory(): Memory =
        Memory(
            id = id,
            title = content
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.take(32)
                ?: "时间线记录",
            content = content,
            createdAt = Instant.ofEpochMilli(createdAtEpochMs),
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
            type = MemoryType.Moment,
            tags = emptyList(),
            mood = "",
            scene = "",
            localOnly = syncState != MemorySyncStatus.SYNCED.name,
            syncStatus = when (syncState) {
                "SYNCED" -> MemorySyncStatus.SYNCED
                "ERROR" -> MemorySyncStatus.FAILED
                else -> MemorySyncStatus.LOCAL_ONLY
            },
        )

    private fun MemoryEntryEntity.toRemoteDto(userId: String): MemoryDto =
        MemoryDto(
            id = id,
            userId = userId,
            title = title,
            content = content,
            type = type,
            tags = tags.fromStorageValue(),
            mood = mood,
            scene = scene,
            createdAt = Instant.ofEpochMilli(createdAt).toString(),
            updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
            deletedAt = deletedAt?.let { Instant.ofEpochMilli(it).toString() },
        )

    private fun MemoryDto.toEntity(
        ownerId: String,
        lastSyncedAt: Long,
    ): MemoryEntryEntity {
        val createdAtMs = createdAt.toEpochMilliOr(Instant.now().toEpochMilli())
        val updatedAtMs = updatedAt.toEpochMilliOr(createdAtMs)
        return MemoryEntryEntity(
            id = id,
            ownerId = ownerId,
            title = title,
            content = content,
            createdAt = createdAtMs,
            updatedAt = updatedAtMs,
            type = type,
            tags = tags.toStorageValue(),
            mood = mood,
            scene = scene,
            localOnly = false,
            syncStatus = MemorySyncStatus.SYNCED.name,
            deletedAt = deletedAt?.toEpochMilliOr(updatedAtMs),
            lastSyncedAt = lastSyncedAt,
            lastSyncAttemptAt = null,
            syncError = "",
        )
    }

    private fun LegacyLifeStreamRow.toMemoryEntity(
        userId: String,
        lastSyncedAt: Long,
    ): MemoryEntryEntity {
        val updatedAtMs = updatedAt
            ?.toEpochMilliOr(createdAtEpochMs)
            ?: createdAtEpochMs
        val normalizedContent = content.trim()
        return MemoryEntryEntity(
            id = entryId,
            ownerId = userId,
            title = normalizedContent
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.take(32)
                ?: "时间线记录",
            content = normalizedContent,
            createdAt = createdAtEpochMs,
            updatedAt = updatedAtMs,
            type = MemoryType.Moment.name,
            tags = "",
            mood = "",
            scene = "",
            localOnly = false,
            syncStatus = MemorySyncStatus.SYNCED.name,
            deletedAt = deletedAtEpochMs,
            lastSyncedAt = lastSyncedAt,
            lastSyncAttemptAt = null,
            syncError = "",
        )
    }

    private fun MemoryEntryEntity.toConflictCopy(
        userId: String,
        now: Long,
    ): MemoryEntryEntity =
        copy(
            id = UUID.randomUUID().toString(),
            ownerId = userId,
            title = title.ifBlank { "本地保留" }.let { "本地保留：$it" },
            createdAt = now,
            updatedAt = now,
            localOnly = false,
            syncStatus = MemorySyncStatus.PENDING.name,
            deletedAt = null,
            lastSyncedAt = null,
            lastSyncAttemptAt = null,
            syncError = "",
        )

    private fun List<MemoryEntryEntity>.toSyncSummary(runtime: SyncRuntimeState): MemorySyncSummary {
        val active = filter { it.deletedAt == null }
        val dirtyStatuses = setOf(MemorySyncStatus.PENDING.name, MemorySyncStatus.FAILED.name)
        return MemorySyncSummary(
            localOnlyCount = active.count { it.syncStatus == MemorySyncStatus.LOCAL_ONLY.name },
            pendingCount = count { it.syncStatus == MemorySyncStatus.PENDING.name },
            syncedCount = active.count { it.syncStatus == MemorySyncStatus.SYNCED.name },
            failedCount = count { it.syncStatus == MemorySyncStatus.FAILED.name },
            isSyncing = runtime.isSyncing,
            lastSyncedAt = mapNotNull { it.lastSyncedAt }.maxOrNull()?.let(Instant::ofEpochMilli),
            lastError = runtime.lastError
                ?: firstOrNull { it.syncStatus in dirtyStatuses && it.syncError.isNotBlank() }?.syncError,
        )
    }

    private fun MemoryEntryEntity.toExportItem(): MemoryExportItem =
        MemoryExportItem(
            id = id,
            title = title,
            content = content,
            type = type,
            tags = tags.fromStorageValue(),
            mood = mood,
            scene = scene,
            createdAt = Instant.ofEpochMilli(createdAt).toString(),
            updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
            syncStatus = syncStatus,
        )

    private fun List<String>.toStorageValue(): String =
        map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(separator = ",")

    private fun String.fromStorageValue(): List<String> =
        split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun String.toEpochMilliOr(defaultValue: Long): Long =
        runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(this).toEpochMilli() }
            .getOrDefault(defaultValue)

    private fun Throwable.safeMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "同步失败"
}

private data class SyncRuntimeState(
    val isSyncing: Boolean = false,
    val lastError: String? = null,
)

@Serializable
private data class LegacyLifeStreamRow(
    @SerialName("user_id") val userId: String,
    @SerialName("entry_id") val entryId: String,
    val content: String,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    val type: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("deleted_at_epoch_ms") val deletedAtEpochMs: Long? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class MemoryExportFile(
    val version: Int = 1,
    @SerialName("exported_at") val exportedAt: String,
    val memories: List<MemoryExportItem>,
)

@Serializable
private data class MemoryExportItem(
    val id: String,
    val title: String,
    val content: String,
    val type: String,
    val tags: List<String>,
    val mood: String,
    val scene: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("sync_status") val syncStatus: String,
)
