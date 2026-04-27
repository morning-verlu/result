package cn.verlu.memory.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import cn.verlu.memory.core.log.MemoryLog
import cn.verlu.memory.data.local.dao.MemoryEntryDao
import cn.verlu.memory.data.local.dao.TombstoneDao
import cn.verlu.memory.data.local.entity.MemoryEntryEntity
import cn.verlu.memory.data.local.entity.TombstoneEntity
import cn.verlu.memory.data.local.MemorySettingsStore
import cn.verlu.memory.data.remote.SupabaseConfig
import cn.verlu.memory.domain.model.LifeEntry
import cn.verlu.memory.domain.model.LifeMedia
import cn.verlu.memory.domain.model.LifeEntryType
import cn.verlu.memory.domain.model.SyncState
import cn.verlu.memory.domain.repository.LifeStreamRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.Base64
import kotlin.math.max
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault

@Singleton
class FileLifeStreamRepository(
    private val context: Context,
    private val supabase: SupabaseClient,
    private val memoryEntryDao: MemoryEntryDao,
    private val tombstoneDao: TombstoneDao,
    private val settingsStore: MemorySettingsStore,
) : LifeStreamRepository {
    companion object {
        private const val TAG = "MemoryCloudSync"
        private const val TABLE_MEMORY_ENTRIES = "memory_entries"
        private const val MAX_IMAGE_UPLOAD_BYTES = 5 * 1024 * 1024
        private const val MAX_AUTO_RETRY = 3
        private const val TOMBSTONE_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val mediaDir: File by lazy {
        File(context.filesDir, "memory-media").apply { mkdirs() }
    }
    private val activeUserFile: File by lazy {
        File(context.filesDir, "memory-active-user.txt")
    }
    private val httpClient = HttpClient(OkHttp)

    private fun mapMediaUrl(url: String): String =
        SupabaseConfig.mapMediaUrl(url, settingsStore.getMediaCdnBaseUrl())

    override fun observeEntries(): Flow<List<LifeEntry>> =
        memoryEntryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<LifeEntry> = mutex.withLock {
        val traceId = newTraceId("getAll")
        val startedAt = System.currentTimeMillis()
        val local = readAllLocked()
        MemoryLog.d(TAG, "[$traceId] start local=${local.size}")
        if (!settingsStore.isCloudSyncEnabled()) return@withLock local
        val localTombstones = pruneExpiredTombstones(readTombstonesLocked())
        val userId = supabase.auth.currentUserOrNull()?.id ?: return@withLock local
        val currentActiveUser = readActiveUserIdLocked()
        val remote = fetchRemoteEntriesOrNull(userId) ?: return@withLock local
        MemoryLog.d(TAG, "[$traceId] remote entries=${remote.entries.size} tombstones=${remote.tombstones.size}")

        val merged = if (currentActiveUser != null && currentActiveUser != userId) {
            // 切换账号时优先远端，避免跨账号本地数据串扰
            applyTombstones(remote.entries, remote.tombstones)
        } else {
            val mergedEntries = mergeById(local, remote.entries)
            val mergedTombstones = mergeTombstones(localTombstones, remote.tombstones)
            applyTombstones(mergedEntries, mergedTombstones)
        }

        if (merged != local) writeAllLocked(merged)
        syncLocalEntriesToRemote(userId, merged)
        val mergedTombstones = pruneExpiredTombstones(mergeTombstones(localTombstones, remote.tombstones))
        writeTombstonesLocked(mergedTombstones)
        syncLocalTombstonesToRemote(userId, mergedTombstones)
        deleteExpiredRemoteTombstones(userId)
        writeActiveUserIdLocked(userId)
        MemoryLog.d(TAG, "[$traceId] done merged=${merged.size} elapsedMs=${System.currentTimeMillis() - startedAt}")
        merged
    }

    override suspend fun upsert(entry: LifeEntry) {
        mutex.withLock {
            val traceId = newTraceId("upsert:${entry.id.take(8)}")
            val startedAt = System.currentTimeMillis()
            val cloudEnabled = settingsStore.isCloudSyncEnabled()
            var stored = entry.copy(syncState = SyncState.LOCAL_ONLY, retryCount = 0)
            MemoryLog.d(TAG, "[$traceId] start cloudEnabled=$cloudEnabled type=${entry.type} media=${entry.mediaList.size}")
            val current = readAllLocked().toMutableList()
            val existingIndex = current.indexOfFirst { it.id == entry.id }
            if (existingIndex >= 0) {
                current[existingIndex] = stored
            } else {
                current += stored
            }
            if (cloudEnabled && canStartManualSync(stored)) {
                val syncing = stored.copy(syncState = SyncState.SYNCING)
                MemoryLog.d(TAG, "[$traceId] state ${stored.syncState} -> ${syncing.syncState}")
                if (existingIndex >= 0) current[existingIndex] = syncing else current[current.lastIndex] = syncing
                writeAllLocked(current)
                stored = runCatching {
                    val normalized = withContext(Dispatchers.IO) { uploadMediaIfNeeded(syncing) }
                    val remoteSuccess = upsertRemoteEntry(normalized)
                    normalized.copy(
                        syncState = if (remoteSuccess && isEntryFullyCloudSynced(normalized)) {
                            SyncState.SYNCED
                        } else {
                            SyncState.ERROR
                        },
                        retryCount = if (remoteSuccess && isEntryFullyCloudSynced(normalized)) 0 else syncing.retryCount + 1,
                    )
                }.getOrElse {
                    syncing.copy(syncState = SyncState.ERROR, retryCount = syncing.retryCount + 1)
                }
                MemoryLog.d(TAG, "[$traceId] finalize state=${stored.syncState} retry=${stored.retryCount}")
                if (existingIndex >= 0) current[existingIndex] = stored else current[current.lastIndex] = stored
            }
            val localMerged = current.sortedByDescending { it.createdAtEpochMs }
            writeAllLocked(localMerged)
            val updatedTombstones = readTombstonesLocked().filterNot { it.entryId == stored.id }
            writeTombstonesLocked(updatedTombstones)
            MemoryLog.d(TAG, "[$traceId] done elapsedMs=${System.currentTimeMillis() - startedAt}")
        }
    }

    override suspend fun delete(entryId: String) {
        mutex.withLock {
            val traceId = newTraceId("delete:${entryId.take(8)}")
            MemoryLog.d(TAG, "[$traceId] start")
            val kept = readAllLocked().filterNot { it.id == entryId }
            writeAllLocked(kept)
            val deletedAt = System.currentTimeMillis()
            val tombstones = pruneExpiredTombstones(readTombstonesLocked()).associateBy { it.entryId }.toMutableMap()
            val old = tombstones[entryId]
            if (old == null || deletedAt >= old.deletedAtEpochMs) {
                tombstones[entryId] = TombstoneRecord(entryId = entryId, deletedAtEpochMs = deletedAt)
                writeTombstonesLocked(tombstones.values.toList())
            }
            upsertRemoteTombstone(entryId, deletedAt)
            val userId = supabase.auth.currentUserOrNull()?.id
            if (userId != null) deleteExpiredRemoteTombstones(userId)
            MemoryLog.d(TAG, "[$traceId] done kept=${kept.size} deletedAt=$deletedAt")
        }
    }

    override suspend fun exportToJson(): String = mutex.withLock {
        json.encodeToString(readAllLocked().sortedByDescending { it.createdAtEpochMs })
    }

    override suspend fun importFromJson(json: String): Int =
        mutex.withLock {
            val imported = this.json.decodeFromString<List<LifeEntry>>(json)
            val merged = readAllLocked().associateBy { it.id }.toMutableMap()
            imported.forEach { merged[it.id] = it }
            val mergedList = merged.values.sortedByDescending { it.createdAtEpochMs }
            writeAllLocked(mergedList)
            imported.size
        }

    override suspend fun syncPendingMedia(): Int =
        mutex.withLock {
            val traceId = newTraceId("syncNow")
            val startedAt = System.currentTimeMillis()
            if (!settingsStore.isCloudSyncEnabled()) return@withLock 0
            val current = readAllLocked()
            MemoryLog.d(TAG, "[$traceId] start candidates=${current.size}")
            var changed = 0
            val synced = withContext(Dispatchers.IO) {
                current.map { entry ->
                    if (!shouldAutoRetry(entry)) return@map entry
                    MemoryLog.d(TAG, "[$traceId] entry=${entry.id.take(8)} state=${entry.syncState} retry=${entry.retryCount}")
                    val syncing = entry.copy(syncState = SyncState.SYNCING)
                    val finalized = runCatching {
                        val normalized = uploadMediaIfNeeded(syncing)
                        val remoteSuccess = upsertRemoteEntry(normalized)
                        normalized.copy(
                            syncState = if (remoteSuccess && isEntryFullyCloudSynced(normalized)) {
                                SyncState.SYNCED
                            } else {
                                SyncState.ERROR
                            },
                            retryCount = if (remoteSuccess && isEntryFullyCloudSynced(normalized)) 0 else syncing.retryCount + 1,
                        )
                    }.getOrElse {
                        syncing.copy(syncState = SyncState.ERROR, retryCount = syncing.retryCount + 1)
                    }
                    if (finalized != entry) changed++
                    MemoryLog.d(TAG, "[$traceId] entry=${entry.id.take(8)} -> ${finalized.syncState} retry=${finalized.retryCount}")
                    finalized
                }
            }
            if (changed > 0) {
                writeAllLocked(synced)
            }
            syncLocalEntriesToRemote(synced)
            val userId = supabase.auth.currentUserOrNull()?.id
            val localTombstones = pruneExpiredTombstones(readTombstonesLocked())
            val remotePayload = userId?.let { fetchRemoteEntriesOrNull(it) }
            val merged = mergeById(synced, remotePayload?.entries.orEmpty())
            val mergedTombstones = pruneExpiredTombstones(mergeTombstones(localTombstones, remotePayload?.tombstones.orEmpty()))
            val visibleMerged = applyTombstones(merged, mergedTombstones)
            if (visibleMerged != synced) {
                writeAllLocked(visibleMerged)
            }
            writeTombstonesLocked(mergedTombstones)
            if (userId != null) {
                syncLocalTombstonesToRemote(userId, mergedTombstones)
                deleteExpiredRemoteTombstones(userId)
            }
            MemoryLog.d(TAG, "[$traceId] done changed=$changed visible=${visibleMerged.size} elapsedMs=${System.currentTimeMillis() - startedAt}")
            changed
        }

    override suspend fun syncEntry(entryId: String): Boolean = mutex.withLock {
        val traceId = newTraceId("syncOne:${entryId.take(8)}")
        val startedAt = System.currentTimeMillis()
        if (!settingsStore.isCloudSyncEnabled()) return@withLock false
        val current = readAllLocked().toMutableList()
        val index = current.indexOfFirst { it.id == entryId }
        if (index < 0) return@withLock false
        val target = current[index]
        if (!canStartManualSync(target)) return@withLock target.syncState == SyncState.SYNCED
        MemoryLog.d(TAG, "[$traceId] start state=${target.syncState} retry=${target.retryCount}")
        val syncing = target.copy(syncState = SyncState.SYNCING)
        current[index] = syncing
        writeAllLocked(current)
        val synced = runCatching {
            val normalized = withContext(Dispatchers.IO) { uploadMediaIfNeeded(syncing) }
            val remoteSuccess = upsertRemoteEntry(normalized)
            normalized.copy(
                syncState = if (remoteSuccess && isEntryFullyCloudSynced(normalized)) {
                    SyncState.SYNCED
                } else {
                    SyncState.ERROR
                },
                retryCount = if (remoteSuccess && isEntryFullyCloudSynced(normalized)) 0 else syncing.retryCount + 1,
            )
        }.getOrElse {
            syncing.copy(syncState = SyncState.ERROR, retryCount = syncing.retryCount + 1)
        }
        current[index] = synced
        writeAllLocked(current)
        MemoryLog.d(TAG, "[$traceId] done state=${synced.syncState} retry=${synced.retryCount} elapsedMs=${System.currentTimeMillis() - startedAt}")
        synced.syncState == SyncState.SYNCED
    }

    private suspend fun readAllLocked(): List<LifeEntry> =
        memoryEntryDao.getAll().map { it.toDomain() }

    private suspend fun writeAllLocked(items: List<LifeEntry>) {
        val sorted = items.sortedByDescending { it.createdAtEpochMs }
        if (sorted.isEmpty()) {
            memoryEntryDao.deleteAll()
            return
        }
        val entities = sorted.map { it.toEntity() }
        memoryEntryDao.upsertAll(entities)
        memoryEntryDao.deleteNotInIds(entities.map { it.id })
    }

    private suspend fun readTombstonesLocked(): List<TombstoneRecord> =
        tombstoneDao.getAll().map { TombstoneRecord(entryId = it.entryId, deletedAtEpochMs = it.deletedAtEpochMs) }

    private suspend fun writeTombstonesLocked(items: List<TombstoneRecord>) {
        val sorted = items.sortedByDescending { it.deletedAtEpochMs }
        tombstoneDao.deleteAll()
        if (sorted.isNotEmpty()) {
            tombstoneDao.upsertAll(sorted.map { TombstoneEntity(entryId = it.entryId, deletedAtEpochMs = it.deletedAtEpochMs) })
        }
    }

    private suspend fun uploadMediaIfNeeded(entry: LifeEntry): LifeEntry {
        if (entry.mediaList.isEmpty()) return entry
        val traceId = newTraceId("media:${entry.id.take(8)}")
        val userId = supabase.auth.currentUserOrNull()?.id ?: return entry
        val accessToken = supabase.auth.currentSessionOrNull()?.accessToken ?: return entry
        MemoryLog.d(TAG, "[$traceId] start mediaCount=${entry.mediaList.size}")
        val uploadedMedia = entry.mediaList.mapIndexed { index, media ->
            val stableMedia = ensureLocalMediaCopy(media) ?: media
            if (isCloudSyncedUrl(stableMedia.uri)) {
                return@mapIndexed stableMedia
            }
            val stableUri = Uri.parse(stableMedia.uri)
            val bytes = readMediaBytesForUpload(stableUri, stableMedia.mimeType) ?: return@mapIndexed stableMedia
            val ext = inferExtension(stableMedia.mimeType, stableUri)
            val objectPath = buildStableObjectPath(
                entryId = entry.id,
                media = stableMedia,
                index = index,
                ext = ext,
            )
            runCatching {
                uploadViaCloudEdge(
                    accessToken = accessToken,
                    objectPath = objectPath,
                    bytes = bytes,
                    mimeType = stableMedia.mimeType,
                )
                val signedUrl = createDownloadUrlViaCloudEdge(
                    accessToken = accessToken,
                    fullPath = "owners/$userId/$objectPath",
                )
                MemoryLog.d(TAG, "upload success: $objectPath")
                LifeMedia(
                    uri = mapMediaUrl(signedUrl),
                    mimeType = stableMedia.mimeType,
                )
            }.getOrElse {
                MemoryLog.e(TAG, "upload failed: ${stableMedia.uri}", it)
                stableMedia
            }
        }
        return if (uploadedMedia == entry.mediaList) {
            entry
        } else {
            MemoryLog.d(TAG, "[$traceId] updated uploaded=${uploadedMedia.count { isCloudSyncedUrl(it.uri) }}/${uploadedMedia.size}")
            entry.copy(
                mediaList = uploadedMedia,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun ensureLocalMediaCopy(media: LifeMedia): LifeMedia? {
        if (media.uri.startsWith("http://") || media.uri.startsWith("https://") || media.uri.startsWith("file://")) {
            return media
        }
        val uri = Uri.parse(media.uri)
        if (uri.scheme != "content") return media
        val bytes = readBytesByUri(uri) ?: return null
        val ext = inferExtension(media.mimeType, uri)
        val localFile = File(mediaDir, "${System.currentTimeMillis()}-${UUID.randomUUID()}.$ext")
        runCatching { localFile.writeBytes(bytes) }.getOrNull() ?: return null
        return media.copy(uri = Uri.fromFile(localFile).toString())
    }

    private suspend fun readBytesByUri(uri: Uri): ByteArray? =
        runCatching {
            val input: InputStream? = when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri)
                "file" -> uri.path?.let { File(it).takeIf(File::exists)?.inputStream() }
                "http", "https" -> null
                null -> File(uri.toString()).takeIf(File::exists)?.inputStream()
                else -> null
            }
            input?.use { it.readBytes() } ?: when (uri.scheme) {
                "http", "https" -> {
                    val response = httpClient.get(uri.toString())
                    if (!response.status.isSuccess()) {
                        MemoryLog.w(TAG, "download source media failed: ${response.status.value} ${uri}")
                        null
                    } else {
                        response.body<ByteArray>()
                    }
                }
                else -> null
            }
        }.getOrNull()

    private suspend fun readMediaBytesForUpload(uri: Uri, mimeType: String?): ByteArray? {
        val rawBytes = readBytesByUri(uri) ?: return null
        if (mimeType?.startsWith("image/") != true) return rawBytes
        if (rawBytes.size <= MAX_IMAGE_UPLOAD_BYTES) return rawBytes
        val compressed = compressImageToJpegUnderLimit(uri, MAX_IMAGE_UPLOAD_BYTES)
        if (compressed != null) {
            MemoryLog.d(TAG, "image compressed: ${rawBytes.size} -> ${compressed.size} bytes")
            return compressed
        }
        MemoryLog.w(TAG, "image too large and compression failed, keep original bytes: ${rawBytes.size}")
        return rawBytes
    }

    private fun compressImageToJpegUnderLimit(uri: Uri, maxBytes: Int): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxSide = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while ((maxSide / sampleSize) > 2560) sampleSize *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } ?: return null

        var working = decoded
        val qualityCandidates = intArrayOf(92, 84, 76, 68, 60, 52, 44, 36, 28)
        repeat(5) {
            for (quality in qualityCandidates) {
                val out = ByteArrayOutputStream()
                working.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val bytes = out.toByteArray()
                if (bytes.size <= maxBytes) {
                    if (working !== decoded) working.recycle()
                    decoded.recycle()
                    return bytes
                }
            }
            val nextW = (working.width * 0.82f).toInt().coerceAtLeast(320)
            val nextH = (working.height * 0.82f).toInt().coerceAtLeast(320)
            if (nextW >= working.width || nextH >= working.height) return@repeat
            val scaled = Bitmap.createScaledBitmap(working, nextW, nextH, true)
            if (working !== decoded) working.recycle()
            working = scaled
        }
        if (working !== decoded) working.recycle()
        decoded.recycle()
        return null
    }

    private fun inferExtension(mimeType: String?, uri: Uri): String {
        val byMime = when {
            mimeType == null -> null
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            mimeType.contains("gif", ignoreCase = true) -> "gif"
            mimeType.contains("mp4", ignoreCase = true) -> "mp4"
            mimeType.contains("quicktime", ignoreCase = true) -> "mov"
            mimeType.startsWith("video/") -> "mp4"
            mimeType.startsWith("image/") -> "jpg"
            else -> null
        }
        if (byMime != null) return byMime
        val path = uri.lastPathSegment.orEmpty()
        val dot = path.lastIndexOf('.')
        return if (dot >= 0 && dot < path.length - 1) path.substring(dot + 1) else "bin"
    }

    private suspend fun uploadViaCloudEdge(
        accessToken: String,
        objectPath: String,
        bytes: ByteArray,
        mimeType: String?,
    ) {
        val directUploaded = runCatching {
            val uploadUrl = createUploadUrlViaCloudEdge(
                accessToken = accessToken,
                objectPath = objectPath,
                mimeType = mimeType,
            )
            val response = httpClient.put(uploadUrl) {
                if (!mimeType.isNullOrBlank()) {
                    contentType(ContentType.parse(mimeType))
                }
                setBody(bytes)
            }
            check(response.status.isSuccess()) {
                "直传失败: ${response.status.value} ${response.bodyAsText()}"
            }
        }
        if (directUploaded.isSuccess) return

        MemoryLog.w(TAG, "direct upload failed, fallback to edge upload: $objectPath", directUploaded.exceptionOrNull())
        val payload = buildJsonObject {
            put("action", "upload")
            put("path", objectPath)
            put("contentType", mimeType ?: ContentType.Application.OctetStream.toString())
            put("base64", Base64.getEncoder().encodeToString(bytes))
        }
        callCloudEdge(accessToken, payload.toString())
    }

    private suspend fun createUploadUrlViaCloudEdge(
        accessToken: String,
        objectPath: String,
        mimeType: String?,
    ): String {
        val payload = buildJsonObject {
            put("action", "upload-url")
            put("path", objectPath)
            put("contentType", mimeType ?: ContentType.Application.OctetStream.toString())
        }
        val raw = callCloudEdge(accessToken, payload.toString())
        return json.parseToJsonElement(raw).jsonObject["url"]?.toString()?.trim('"')
            ?: error("cloud-files 返回缺少 upload url")
    }

    private suspend fun createDownloadUrlViaCloudEdge(
        accessToken: String,
        fullPath: String,
    ): String {
        val payload = buildJsonObject {
            put("action", "download-url")
            put("path", fullPath)
            put("expiresInSeconds", 31536000)
        }
        val raw = callCloudEdge(accessToken, payload.toString())
        return json.parseToJsonElement(raw).jsonObject["url"]?.toString()?.trim('"')
            ?: error("cloud-files 返回缺少 url 字段")
    }

    private suspend fun callCloudEdge(accessToken: String, payload: String): String {
        val response = httpClient.post("${SupabaseConfig.URL}/functions/v1/cloud-files") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            error("cloud-files 调用失败: ${response.status.value} ${response.bodyAsText()}")
        }
        return response.bodyAsText()
    }

    private fun isCloudSyncedUrl(uri: String): Boolean {
        return uri.startsWith("http://") || uri.startsWith("https://")
    }

    private fun buildStableObjectPath(
        entryId: String,
        media: LifeMedia,
        index: Int,
        ext: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = "${media.uri}|${media.mimeType.orEmpty()}|$index"
        val hash = digest.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        return "memory/$entryId/$index-$hash.$ext"
    }

    private suspend fun fetchRemoteEntriesOrNull(userId: String): RemotePayload? {
        return runCatching {
            withNetworkRetry("fetch-remote") {
            supabase.postgrest[TABLE_MEMORY_ENTRIES].select {
                filter { eq("user_id", userId) }
                order("created_at_epoch_ms", Order.DESCENDING)
            }.decodeList<MemoryEntryRow>().let { rows ->
                val tombstones = rows
                    .filter { it.isDeleted }
                    .map {
                        TombstoneRecord(
                            entryId = it.entryId,
                            deletedAtEpochMs = it.deletedAtEpochMs ?: it.createdAtEpochMs,
                        )
                    }
                val entries = rows
                    .filterNot { it.isDeleted }
                    .map { row ->
                        val domain = row.toDomain()
                        domain.copy(
                            mediaList = domain.mediaList.map { media ->
                                media.copy(uri = mapMediaUrl(media.uri))
                            },
                        )
                    }
                RemotePayload(entries = entries, tombstones = tombstones)
            }
            }
        }.onFailure {
            MemoryLog.w(TAG, "fetch remote entries failed", it)
        }.getOrNull()
    }

    private suspend fun upsertRemoteEntry(entry: LifeEntry): Boolean {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return false
        return runCatching {
            withNetworkRetry("upsert-entry:${entry.id.take(8)}") {
            supabase.postgrest[TABLE_MEMORY_ENTRIES].upsert(
                listOf(
                    entry.toRow(userId = userId),
                ),
            ) {
                onConflict = "user_id,entry_id"
            }
            }
            true
        }.onFailure {
            MemoryLog.w(TAG, "upsert remote entry failed: ${entry.id}", it)
        }.getOrDefault(false)
    }

    private suspend fun upsertRemoteTombstone(entryId: String, deletedAtEpochMs: Long) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        runCatching {
            withNetworkRetry("upsert-tombstone:${entryId.take(8)}") {
            supabase.postgrest[TABLE_MEMORY_ENTRIES].upsert(
                listOf(
                    MemoryEntryRow(
                        userId = userId,
                        entryId = entryId,
                        content = "",
                        createdAtEpochMs = deletedAtEpochMs,
                        type = LifeEntryType.TEXT.name,
                        mediaList = emptyList(),
                        isDeleted = true,
                        deletedAtEpochMs = deletedAtEpochMs,
                    ),
                ),
            ) {
                onConflict = "user_id,entry_id"
            }
            }
        }.onFailure {
            MemoryLog.w(TAG, "upsert remote tombstone failed: $entryId", it)
        }
    }

    private suspend fun syncLocalEntriesToRemote(local: List<LifeEntry>) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        syncLocalEntriesToRemote(userId, local)
    }

    private suspend fun syncLocalEntriesToRemote(userId: String, local: List<LifeEntry>) {
        if (local.isEmpty()) return
        runCatching {
            val rows = local.map { it.toRow(userId) }
            withNetworkRetry("sync-local-entries") {
            supabase.postgrest[TABLE_MEMORY_ENTRIES].upsert(
                rows,
            ) {
                onConflict = "user_id,entry_id"
            }
            }
        }.onFailure {
            MemoryLog.w(TAG, "sync local entries to remote failed", it)
        }
    }

    private suspend fun syncLocalTombstonesToRemote(userId: String, tombstones: List<TombstoneRecord>) {
        if (tombstones.isEmpty()) return
        runCatching {
            val rows = tombstones.map { tombstone ->
                MemoryEntryRow(
                    userId = userId,
                    entryId = tombstone.entryId,
                    content = "",
                    createdAtEpochMs = tombstone.deletedAtEpochMs,
                    type = LifeEntryType.TEXT.name,
                    mediaList = emptyList(),
                    isDeleted = true,
                    deletedAtEpochMs = tombstone.deletedAtEpochMs,
                )
            }
            withNetworkRetry("sync-local-tombstones") {
            supabase.postgrest[TABLE_MEMORY_ENTRIES].upsert(
                rows,
            ) {
                onConflict = "user_id,entry_id"
            }
            }
        }.onFailure {
            MemoryLog.w(TAG, "sync local tombstones to remote failed", it)
        }
    }

    private fun mergeById(local: List<LifeEntry>, remote: List<LifeEntry>): List<LifeEntry> {
        val merged = local.associateBy { it.id }.toMutableMap()
        remote.forEach { remoteEntry ->
            val localEntry = merged[remoteEntry.id]
            merged[remoteEntry.id] = if (localEntry == null) {
                remoteEntry
            } else if (remoteEntry.updatedAtEpochMs >= localEntry.updatedAtEpochMs) {
                remoteEntry
            } else {
                localEntry
            }
        }
        return merged.values.sortedByDescending { it.createdAtEpochMs }
    }

    private fun mergeTombstones(
        local: List<TombstoneRecord>,
        remote: List<TombstoneRecord>,
    ): List<TombstoneRecord> {
        val merged = local.associateBy { it.entryId }.toMutableMap()
        remote.forEach { remoteStone ->
            val localStone = merged[remoteStone.entryId]
            if (localStone == null || remoteStone.deletedAtEpochMs >= localStone.deletedAtEpochMs) {
                merged[remoteStone.entryId] = remoteStone
            }
        }
        return merged.values.toList()
    }

    private fun applyTombstones(entries: List<LifeEntry>, tombstones: List<TombstoneRecord>): List<LifeEntry> {
        if (entries.isEmpty() || tombstones.isEmpty()) return entries
        val deletedMap = tombstones.associateBy { it.entryId }
        return entries.filter { entry ->
            val stone = deletedMap[entry.id] ?: return@filter true
            entry.updatedAtEpochMs > stone.deletedAtEpochMs
        }.sortedByDescending { it.createdAtEpochMs }
    }

    private fun readActiveUserIdLocked(): String? {
        if (!activeUserFile.exists()) return null
        return activeUserFile.readText().trim().ifBlank { null }
    }

    private fun writeActiveUserIdLocked(userId: String) {
        if (!activeUserFile.exists()) {
            activeUserFile.parentFile?.mkdirs()
            activeUserFile.createNewFile()
        }
        activeUserFile.writeText(userId)
    }

    @Serializable
    private data class MemoryEntryRow(
        @SerialName("user_id")
        val userId: String,
        @SerialName("entry_id")
        val entryId: String,
        val content: String,
        @SerialName("created_at_epoch_ms")
        val createdAtEpochMs: Long,
        val type: String,
        @SerialName("media_list")
        @EncodeDefault
        val mediaList: List<LifeMedia> = emptyList(),
        @SerialName("is_deleted")
        val isDeleted: Boolean = false,
        @SerialName("deleted_at_epoch_ms")
        val deletedAtEpochMs: Long? = null,
        @SerialName("updated_at")
        val updatedAt: String? = null,
    ) {
        fun toDomain(): LifeEntry = LifeEntry(
            id = entryId,
            content = content,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = runCatching {
                updatedAt?.let { Instant.parse(it).toEpochMilli() }
            }.getOrNull() ?: createdAtEpochMs,
            syncState = if (isDeleted) SyncState.ERROR else SyncState.SYNCED,
            retryCount = 0,
            type = runCatching { LifeEntryType.valueOf(type) }.getOrDefault(LifeEntryType.TEXT),
            mediaList = mediaList,
        )
    }

    private fun LifeEntry.toRow(userId: String): MemoryEntryRow = MemoryEntryRow(
        userId = userId,
        entryId = id,
        content = content,
        createdAtEpochMs = createdAtEpochMs,
        type = type.name,
        mediaList = mediaList,
        isDeleted = false,
        deletedAtEpochMs = null,
    )

    private fun LifeEntry.toEntity(): MemoryEntryEntity = MemoryEntryEntity(
        id = id,
        content = content,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncState = syncState.name,
        retryCount = retryCount,
        type = type.name,
        mediaListJson = json.encodeToString(mediaList),
    )

    private fun MemoryEntryEntity.toDomain(): LifeEntry = LifeEntry(
        id = id,
        content = content,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.LOCAL_ONLY),
        retryCount = retryCount,
        type = runCatching { LifeEntryType.valueOf(type) }.getOrDefault(LifeEntryType.TEXT),
        mediaList = runCatching { json.decodeFromString<List<LifeMedia>>(mediaListJson) }
            .getOrDefault(emptyList())
            .map { media -> media.copy(uri = mapMediaUrl(media.uri)) },
    )

    @Serializable
    private data class TombstoneRecord(
        val entryId: String,
        val deletedAtEpochMs: Long,
    )

    private data class RemotePayload(
        val entries: List<LifeEntry>,
        val tombstones: List<TombstoneRecord>,
    )

    private fun isEntryFullyCloudSynced(entry: LifeEntry): Boolean {
        if (entry.mediaList.isEmpty()) return true
        return entry.mediaList.all { media -> isCloudSyncedUrl(media.uri) }
    }

    private fun canStartManualSync(entry: LifeEntry): Boolean =
        entry.syncState == SyncState.LOCAL_ONLY || entry.syncState == SyncState.ERROR

    private fun shouldAutoRetry(entry: LifeEntry): Boolean =
        canStartManualSync(entry) && entry.retryCount < MAX_AUTO_RETRY

    private fun pruneExpiredTombstones(items: List<TombstoneRecord>): List<TombstoneRecord> {
        val cutoff = System.currentTimeMillis() - TOMBSTONE_TTL_MS
        return items.filter { it.deletedAtEpochMs >= cutoff }
    }

    private suspend fun deleteExpiredRemoteTombstones(userId: String) {
        val cutoff = System.currentTimeMillis() - TOMBSTONE_TTL_MS
        MemoryLog.d(TAG, "[tombstone-clean] user=${userId.take(8)} cutoff=$cutoff")
        runCatching {
            withNetworkRetry("delete-expired-tombstones") {
            supabase.postgrest[TABLE_MEMORY_ENTRIES].delete {
                filter {
                    eq("user_id", userId)
                    lt("deleted_at_epoch_ms", cutoff)
                }
            }
            }
        }.onFailure {
            MemoryLog.w(TAG, "delete expired remote tombstones failed", it)
        }
    }

    private suspend fun <T> withNetworkRetry(
        label: String,
        maxAttempts: Int = 3,
        block: suspend () -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            runCatching { return block() }
                .onFailure { error ->
                    lastError = error
                    val shouldRetry = attempt < maxAttempts - 1 && isRetryableNetworkError(error)
                    if (shouldRetry) {
                        MemoryLog.w(TAG, "[$label] network retry ${attempt + 1}/$maxAttempts", error)
                        delay((attempt + 1) * 500L)
                    }
                }
        }
        throw (lastError ?: IllegalStateException("[$label] unknown network retry failure"))
    }

    private fun isRetryableNetworkError(error: Throwable): Boolean {
        val text = error.message.orEmpty().lowercase()
        return text.contains("unable to resolve host") ||
            text.contains("unknownhost") ||
            text.contains("timeout") ||
            text.contains("socket")
    }

    private fun newTraceId(prefix: String): String = "$prefix-${System.currentTimeMillis().toString().takeLast(6)}"

}
