package cn.verlu.cloud.presentation.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import cn.verlu.cloud.data.files.FileRepository
import cn.verlu.cloud.data.friends.CloudFriendItem
import cn.verlu.cloud.data.friends.CloudFriendRepository
import cn.verlu.cloud.domain.files.CloudFileItem
import cn.verlu.cloud.platform.openExternalUrl

/** 分享底部弹窗的当前步骤。 */
enum class ShareStep { HIDDEN, OPTIONS, FRIEND_PICKER, SUCCESS }
private const val DEFAULT_SHARE_EXPIRES_IN_SECONDS = 3600
enum class UploadItemStatus { PENDING, UPLOADING, SUCCESS, FAILED }

data class UploadQueueItem(
    val id: String,
    val name: String,
    val sizeBytes: Int,
    val status: UploadItemStatus,
    /** null 表示不展示进度；0..1 表示确定进度。 */
    val progress: Float? = null,
    val sentBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val error: String? = null,
)

data class FileExplorerUiState(
    val files: List<CloudFileItem> = emptyList(),
    val isRefreshing: Boolean = false,
    /** 当前目录（相对路径，根目录为空字符串，子目录如 "docs/"）。 */
    val currentPrefix: String = "",
    val error: String? = null,
    /** 上传进度 0..1，null 表示无上传中。 */
    val uploadProgress: Float? = null,
    val uploadingName: String? = null,
    val uploadBatchTotal: Int = 0,
    val uploadBatchDone: Int = 0,
    val uploadQueue: List<UploadQueueItem> = emptyList(),
    /** 下载/删除/移动等非刷新异步操作的临时提示。 */
    val toast: String? = null,

    // ── 分享流程 ───────────────────────────────────────────────────────────────
    /** 正在分享的文件，null 表示未触发分享流程。 */
    val shareTarget: CloudFileItem? = null,
    /** 已生成的分享/下载 URL，null 表示正在获取或失败。 */
    val shareUrl: String? = null,
    /** 正在从服务器获取 URL。 */
    val isGeneratingShareUrl: Boolean = false,
    /** 好友列表（分享时懒加载）。 */
    val friends: List<CloudFriendItem> = emptyList(),
    /** 好友列表正在加载。 */
    val isLoadingFriends: Boolean = false,
    /** 分享弹窗当前所在步骤。 */
    val shareStep: ShareStep = ShareStep.HIDDEN,
    /** 正在向某好友发消息。 */
    val isSendingShare: Boolean = false,
    /** 分享成功后的目标好友（用于展示成功界面）。 */
    val shareSentToFriend: CloudFriendItem? = null,
    /** 分享流程中的错误提示。 */
    val shareError: String? = null,
    /** 当前选择的分享有效期（秒）。 */
    val shareExpiresInSeconds: Int = DEFAULT_SHARE_EXPIRES_IN_SECONDS,
    /** 可选有效期列表（秒）。 */
    val shareExpiryOptions: List<Int> = listOf(1800, 3600, 86400, 604800),
    /** 新建后自动进入的文件夹名（用于 UI 高亮）。 */
    val highlightedFolderName: String? = null,
    /** 删除进行中。 */
    val isDeleting: Boolean = false,
    /** 新建文件夹进行中。 */
    val isCreatingFolder: Boolean = false,
    val creatingFolderName: String? = null,
)

class FileExplorerState(
    private val ownerId: String,
    private val fileRepository: FileRepository,
    private val friendRepository: CloudFriendRepository,
) {
    private var refreshJob: Job? = null
    private val uploadRetryCache = LinkedHashMap<String, FilePickResult>()
    private val uploadProgressSnapshot = LinkedHashMap<String, Pair<Long, Long>>()
    private var cachedAllFiles: List<CloudFileItem> = emptyList()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(FileExplorerUiState())
    val state: StateFlow<FileExplorerUiState> = _state.asStateFlow()

    init {
        fileRepository.observeFiles(ownerId)
            .onEach { files ->
                cachedAllFiles = files
                _state.update { ui -> ui.copy(files = filterByPrefix(files, ui.currentPrefix)) }
            }
            .launchIn(scope)

        refresh(showLoading = true)
    }

    /* ── 导航 ─────────────────────────────────────────────────── */

    /** 进入子目录。[folder] 为相对路径，以 "/" 结尾，如 `"docs/"` 。 */
    fun navigateTo(folder: String) {
        _state.update { ui ->
            val raw = folder.trim().removePrefix("/")
            val normalized = if (raw.endsWith("/")) raw else "$raw/"
            val target = if (normalized.startsWith(ui.currentPrefix)) normalized else ui.currentPrefix + normalized
            ui.copy(
                currentPrefix = target,
                files = filterByPrefix(cachedAllFiles, target),
            )
        }
        refresh(showLoading = false)
    }

    /** 返回上级目录。若已在根目录则无操作。 */
    fun navigateUp() {
        val current = _state.value.currentPrefix
        if (current.isEmpty()) return
        val parent = current.trimEnd('/').substringBeforeLast("/", "").let {
            if (it.isEmpty()) "" else "$it/"
        }
        _state.update { ui ->
            ui.copy(
                currentPrefix = parent,
                files = filterByPrefix(cachedAllFiles, parent),
            )
        }
        refresh(showLoading = false)
    }

    /* ── 刷新 ─────────────────────────────────────────────────── */

    fun refresh(showLoading: Boolean = true) {
        scope.launch {
            refreshJob?.cancelAndJoin()
            refreshJob = launch {
                if (showLoading) _state.update { it.copy(isRefreshing = true, error = null) }
                try {
                    withTimeout(20_000L) {
                        fileRepository.refreshFiles(ownerId, _state.value.currentPrefix)
                            .onFailure { e ->
                                _state.update { it.copy(error = e.message ?: "刷新失败") }
                            }
                    }
                } catch (_: Throwable) {
                    _state.update { it.copy(error = "刷新超时或失败，请重试") }
                } finally {
                    if (showLoading) _state.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    /* ── 上传 ─────────────────────────────────────────────────── */

    fun uploadFile(pick: FilePickResult) {
        uploadFiles(listOf(pick))
    }

    fun uploadFiles(picks: List<FilePickResult>) {
        if (picks.isEmpty()) return
        scope.launch {
            val queued = picks.map { pick ->
                val id = "${kotlin.time.Clock.System.now().toEpochMilliseconds()}-${pick.name}-${pick.sizeBytes}"
                uploadRetryCache[id] = pick
                UploadQueueItem(
                    id = id,
                    name = toSafeObjectName(pick.name),
                    sizeBytes = pick.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    status = UploadItemStatus.PENDING,
                    progress = 0f,
                    sentBytes = 0L,
                    totalBytes = pick.sizeBytes,
                    speedBytesPerSec = 0L,
                )
            }
            val initialQueue = _state.value.uploadQueue + queued
            val total = initialQueue.size
            var done = initialQueue.count { it.status == UploadItemStatus.SUCCESS }

            _state.update {
                it.copy(
                    uploadProgress = 0f,
                    uploadBatchTotal = total,
                    uploadBatchDone = done,
                    uploadingName = queued.firstOrNull()?.name,
                    uploadQueue = initialQueue,
                    error = null,
                )
            }
            queued.forEach { item ->
                val pick = uploadRetryCache[item.id] ?: return@forEach
                _state.update { ui ->
                    ui.copy(
                        uploadingName = item.name,
                        uploadQueue = ui.uploadQueue.map {
                            if (it.id == item.id) it.copy(status = UploadItemStatus.UPLOADING, progress = 0f, error = null) else it
                        },
                    )
                }
                val remotePath = _state.value.currentPrefix + item.name
                val result = fileRepository.uploadFile(
                    ownerId = ownerId,
                    relativePath = remotePath,
                    sizeBytes = pick.sizeBytes,
                    readRange = pick.readRange,
                    contentType = pick.mimeType,
                ) { sentBytes, totalBytes ->
                    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    val speed = uploadProgressSnapshot[item.id]?.let { (prevMs, prevSent) ->
                        val deltaMs = (nowMs - prevMs).coerceAtLeast(1L)
                        val deltaBytes = (sentBytes - prevSent).coerceAtLeast(0L)
                        (deltaBytes * 1000L / deltaMs).coerceAtLeast(0L)
                    } ?: 0L
                    uploadProgressSnapshot[item.id] = nowMs to sentBytes
                    val p = if (totalBytes > 0) (sentBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                    _state.update { ui ->
                        ui.copy(uploadQueue = ui.uploadQueue.map {
                            if (it.id == item.id) {
                                it.copy(
                                    progress = p,
                                    sentBytes = sentBytes,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed,
                                )
                            } else it
                        })
                    }
                }
                if (result.isSuccess) {
                    done += 1
                    _state.update { ui ->
                        ui.copy(
                            uploadBatchDone = done,
                            uploadProgress = done.toFloat() / total.toFloat(),
                            uploadQueue = ui.uploadQueue.map {
                                if (it.id == item.id) it.copy(status = UploadItemStatus.SUCCESS, progress = 1f, error = null) else it
                            },
                        )
                    }
                    uploadProgressSnapshot.remove(item.id)
                } else {
                    val message = result.exceptionOrNull()?.message ?: "未知错误"
                    _state.update { ui ->
                        ui.copy(
                            error = "上传失败：$message",
                            uploadQueue = ui.uploadQueue.map {
                                if (it.id == item.id) it.copy(status = UploadItemStatus.FAILED, progress = 0f, error = message) else it
                            },
                        )
                    }
                    uploadProgressSnapshot.remove(item.id)
                }
            }
            _state.update {
                it.copy(
                    toast = "✓ 已完成上传：$done/$total",
                    uploadProgress = null,
                    uploadingName = null,
                    uploadBatchTotal = 0,
                    uploadBatchDone = 0,
                )
            }
        }
    }

    fun retryUpload(itemId: String) {
        val pick = uploadRetryCache[itemId] ?: return
        val queueItem = _state.value.uploadQueue.firstOrNull { it.id == itemId } ?: return
        scope.launch {
            _state.update { ui ->
                ui.copy(
                    uploadProgress = 0f,
                    uploadBatchTotal = 1,
                    uploadBatchDone = 0,
                    uploadingName = queueItem.name,
                    uploadQueue = ui.uploadQueue.map {
                        if (it.id == itemId) it.copy(status = UploadItemStatus.UPLOADING, progress = 0f, speedBytesPerSec = 0L, error = null) else it
                    },
                )
            }
            val remotePath = _state.value.currentPrefix + queueItem.name
            fileRepository.uploadFile(
                ownerId = ownerId,
                relativePath = remotePath,
                sizeBytes = pick.sizeBytes,
                readRange = pick.readRange,
                contentType = pick.mimeType,
            ) { sentBytes, totalBytes ->
                val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
                val speed = uploadProgressSnapshot[itemId]?.let { (prevMs, prevSent) ->
                    val deltaMs = (nowMs - prevMs).coerceAtLeast(1L)
                    val deltaBytes = (sentBytes - prevSent).coerceAtLeast(0L)
                    (deltaBytes * 1000L / deltaMs).coerceAtLeast(0L)
                } ?: 0L
                uploadProgressSnapshot[itemId] = nowMs to sentBytes
                val p = if (totalBytes > 0) (sentBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                _state.update { ui ->
                    ui.copy(uploadQueue = ui.uploadQueue.map {
                        if (it.id == itemId) {
                            it.copy(
                                progress = p,
                                sentBytes = sentBytes,
                                totalBytes = totalBytes,
                                speedBytesPerSec = speed,
                            )
                        } else it
                    })
                }
            }
                .onSuccess {
                    _state.update { ui ->
                        ui.copy(
                            toast = "✓ 重试成功：${queueItem.name}",
                            uploadQueue = ui.uploadQueue.map {
                                if (it.id == itemId) it.copy(status = UploadItemStatus.SUCCESS, progress = 1f, error = null) else it
                            },
                        )
                    }
                    uploadProgressSnapshot.remove(itemId)
                }
                .onFailure { e ->
                    _state.update { ui ->
                        ui.copy(
                            error = "重试失败：${e.message}",
                            uploadQueue = ui.uploadQueue.map {
                                if (it.id == itemId) it.copy(status = UploadItemStatus.FAILED, progress = 0f, error = e.message) else it
                            },
                        )
                    }
                    uploadProgressSnapshot.remove(itemId)
                }
            _state.update { it.copy(uploadProgress = null, uploadingName = null, uploadBatchTotal = 0, uploadBatchDone = 0) }
        }
    }

    fun clearUploadQueue() {
        _state.update { it.copy(uploadQueue = emptyList()) }
        uploadRetryCache.clear()
        uploadProgressSnapshot.clear()
    }

    fun createFolder(rawName: String) {
        val folderName = toSafeObjectName(rawName)
        if (folderName.isBlank()) {
            _state.update { it.copy(error = "文件夹名称不能为空") }
            return
        }
        val remotePath = _state.value.currentPrefix + folderName.trimEnd('/') + "/"
        scope.launch {
            _state.update { it.copy(error = null, isCreatingFolder = true, creatingFolderName = folderName) }
            // Edge Function 对空 base64 视为缺参，目录占位对象写入 1 字节避免 Missing base64。
            fileRepository.uploadFile(
                ownerId = ownerId,
                relativePath = remotePath,
                sizeBytes = 1L,
                readRange = { _, _ -> byteArrayOf(0) },
                contentType = "application/x-directory",
            )
                .onSuccess {
                    val nextPrefix = _state.value.currentPrefix + folderName.trimEnd('/') + "/"
                    _state.update {
                        it.copy(
                            currentPrefix = nextPrefix,
                            highlightedFolderName = folderName,
                            toast = "✓ 已创建并进入：$folderName",
                            isCreatingFolder = false,
                            creatingFolderName = null,
                        )
                    }
                    refresh()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            error = "新建文件夹失败：${e.message}",
                            isCreatingFolder = false,
                            creatingFolderName = null,
                        )
                    }
                }
        }
    }

    /* ── 下载 ─────────────────────────────────────────────────── */

    fun downloadFile(file: CloudFileItem) {
        scope.launch {
            fileRepository.getDownloadUrl(file.path)
                .onSuccess { url ->
                    openExternalUrl(url)
                        .onFailure { _state.update { s -> s.copy(error = "无法打开浏览器") } }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = "获取下载链接失败：${e.message}") }
                }
        }
    }

    /* ── 删除 ─────────────────────────────────────────────────── */

    fun deleteFile(file: CloudFileItem) {
        scope.launch {
            _state.update { it.copy(error = null, isDeleting = true) }
            fileRepository.deleteFile(ownerId, file.path)
                .onSuccess {
                    _state.update { it.copy(toast = "已删除：${file.fileName}") }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = "删除失败：${e.message}") }
                }
            _state.update { it.copy(isDeleting = false) }
        }
    }

    /* ── 重命名 ───────────────────────────────────────────────── */

    fun renameFile(file: CloudFileItem, newName: String) {
        val toPath = _state.value.currentPrefix + newName
        scope.launch {
            fileRepository.moveFile(ownerId, file.path, toPath)
                .onSuccess {
                    _state.update { it.copy(toast = "已重命名为：$newName") }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = "重命名失败：${e.message}") }
                }
        }
    }

    /* ── 分享 ─────────────────────────────────────────────────── */

    /**
     * 触发对 [file] 的分享流程：获取预签名下载 URL，打开分享弹窗。
     * 仅对文件有效，文件夹分享暂不支持。
     */
    fun initiateShare(file: CloudFileItem) {
        if (file.isDir) {
            _state.update { it.copy(toast = "暂不支持分享文件夹") }
            return
        }
        _state.update {
            it.copy(
                shareTarget = file,
                shareStep = ShareStep.OPTIONS,
                shareUrl = null,
                isGeneratingShareUrl = true,
                shareError = null,
                shareSentToFriend = null,
            )
        }
        scope.launch {
            fileRepository.getDownloadUrl(file.path, _state.value.shareExpiresInSeconds)
                .onSuccess { url ->
                    _state.update { it.copy(shareUrl = url, isGeneratingShareUrl = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isGeneratingShareUrl = false,
                            shareError = "获取分享链接失败：${e.message}",
                        )
                    }
                }
        }
    }

    /** 修改分享链接有效期，并在已有分享目标时自动重新生成链接。 */
    fun setShareExpiry(seconds: Int) {
        _state.update { it.copy(shareExpiresInSeconds = seconds.coerceIn(60, 604800)) }
        regenerateShareUrlIfNeeded()
    }

    private fun regenerateShareUrlIfNeeded() {
        val target = _state.value.shareTarget ?: return
        scope.launch {
            _state.update { it.copy(isGeneratingShareUrl = true, shareError = null) }
            fileRepository.getDownloadUrl(target.path, _state.value.shareExpiresInSeconds)
                .onSuccess { url ->
                    _state.update { it.copy(shareUrl = url, isGeneratingShareUrl = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isGeneratingShareUrl = false,
                            shareError = "获取分享链接失败：${e.message}",
                        )
                    }
                }
        }
    }

    /** 切换到好友选择页（同时懒加载好友列表）。 */
    fun openFriendPicker() {
        _state.update { it.copy(shareStep = ShareStep.FRIEND_PICKER, shareError = null) }
        if (_state.value.friends.isEmpty()) loadFriends()
    }

    /** 返回分享选项页。 */
    fun backToShareOptions() {
        _state.update { it.copy(shareStep = ShareStep.OPTIONS, shareError = null) }
    }

    private fun loadFriends() {
        scope.launch {
            _state.update { it.copy(isLoadingFriends = true) }
            friendRepository.getAcceptedFriends()
                .onSuccess { friends ->
                    _state.update { it.copy(friends = friends, isLoadingFriends = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoadingFriends = false,
                            shareError = "加载好友列表失败：${e.message}",
                        )
                    }
                }
        }
    }

    /**
     * 将分享链接作为消息发送给 [friend]。
     * [friend] 必须有有效的 [CloudFriendItem.roomId]。
     */
    fun sendShareToFriend(friend: CloudFriendItem) {
        val roomId = friend.roomId
        if (roomId == null) {
            _state.update { it.copy(shareError = "与该好友暂无聊天室，请先在 Talk 中发起会话后重试") }
            return
        }
        val shareUrl = _state.value.shareUrl
        val file = _state.value.shareTarget
        if (shareUrl == null || file == null) return

        scope.launch {
            _state.update { it.copy(isSendingShare = true, shareError = null) }
            val content = buildShareMessageContent(file, shareUrl)
            friendRepository.sendMessageToRoom(roomId, content)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isSendingShare = false,
                            shareSentToFriend = friend,
                            shareStep = ShareStep.SUCCESS,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isSendingShare = false,
                            shareError = "发送失败：${e.message}",
                        )
                    }
                }
        }
    }

    /** 关闭分享弹窗并重置所有分享状态。 */
    fun dismissShare() {
        _state.update {
            it.copy(
                shareTarget = null,
                shareUrl = null,
                isGeneratingShareUrl = false,
                friends = emptyList(),
                isLoadingFriends = false,
                shareStep = ShareStep.HIDDEN,
                isSendingShare = false,
                shareSentToFriend = null,
                shareError = null,
            )
        }
    }

    fun clearShareError() = _state.update { it.copy(shareError = null) }

    /* ── 其他 ─────────────────────────────────────────────────── */

    fun clearToast() = _state.update { it.copy(toast = null) }
    fun clearError() = _state.update { it.copy(error = null) }
    fun clearFolderHighlight() = _state.update { it.copy(highlightedFolderName = null) }

    /* ── 内部工具 ─────────────────────────────────────────────── */

    private fun buildShareMessageContent(file: CloudFileItem, url: String): String {
        val sizeStr = formatBytes(file.sizeBytes)
        val expiryText = formatExpiryLabel(_state.value.shareExpiresInSeconds)
        return "📎 通过 Verlu Cloud 与你分享了文件\n\n文件名：${file.fileName}\n大小：$sizeStr\n有效期：$expiryText\n\n下载链接：$url"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var u = 0
        while (v >= 1024 && u < units.lastIndex) {
            v /= 1024
            u++
        }
        return if (u == 0) "$bytes ${units[u]}" else "%.1f %s".format(v, units[u])
    }

    fun formatExpiryLabel(seconds: Int): String = when (seconds) {
        1800 -> "30 分钟"
        3600 -> "1 小时"
        86400 -> "24 小时"
        604800 -> "7 天"
        else -> "${seconds} 秒"
    }

    /**
     * 预签名 URL 对路径编码很敏感。
     * 统一将对象名收敛为安全字符集。
     */
    private fun toSafeObjectName(raw: String): String {
        val cleaned = raw.trim().ifBlank { "upload-${kotlin.time.Clock.System.now().toEpochMilliseconds()}" }
        val mapped = buildString(cleaned.length) {
            cleaned.forEach { c ->
                val keep = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '.' || c == '_' || c == '-'
                append(if (keep) c else '_')
            }
        }
        return mapped.trim('_').ifBlank { "upload-${kotlin.time.Clock.System.now().toEpochMilliseconds()}" }
    }

    private fun filterByPrefix(all: List<CloudFileItem>, prefix: String): List<CloudFileItem> {
        return all
            .filter { parentPrefix(it.path) == prefix }
            .sortedWith(compareByDescending<CloudFileItem> { it.isDir }.thenBy { it.fileName.lowercase() })
    }

    private fun parentPrefix(path: String): String {
        val noTail = path.trim().trimEnd('/')
        if (noTail.isEmpty()) return ""
        val parent = noTail.substringBeforeLast("/", "")
        return if (parent.isEmpty()) "" else "$parent/"
    }

}
