package cn.verlu.memory.presentation.lifestream.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.memory.core.log.MemoryLog
import cn.verlu.memory.data.local.MemorySettingsStore
import cn.verlu.memory.domain.model.LifeEntry
import cn.verlu.memory.domain.model.LifeMedia
import cn.verlu.memory.domain.model.LifeEntryType
import cn.verlu.memory.domain.model.SyncState
import cn.verlu.memory.domain.repository.LifeStreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import java.net.UnknownHostException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val INPUT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val SINGLE_ENTRY_EXPORT_JSON = Json { prettyPrint = true }

data class LifeStreamUiState(
    val allEntries: List<LifeEntry> = emptyList(),
    val searchKeyword: String = "",
    val searchTimeFilter: SearchTimeFilter = SearchTimeFilter.ALL,
    val editingEntryId: String? = null,
    val draftContent: String = "",
    val draftTimeText: String = nowInputTime(),
    val draftMediaList: List<LifeMedia> = emptyList(),
    val isDiscardDialogVisible: Boolean = false,
    val isTimeDialogVisible: Boolean = false,
    val customTimeInput: String = nowInputTime(),
    val recordCloseNonce: Int = 0,
    val scrollToTopNonce: Int = 0,
    val pendingSyncCount: Int = 0,
    val showCloudBadge: Boolean = true,
    val cloudSyncEnabled: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isSavingRecord: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val timelineEntries: List<LifeEntry>
        get() = allEntries.sortedByDescending { it.createdAtEpochMs }

    val searchResults: List<LifeEntry>
        get() {
            val now = System.currentTimeMillis()
            val recentFrom = when (searchTimeFilter) {
                SearchTimeFilter.ALL -> Long.MIN_VALUE
                SearchTimeFilter.DAYS_7 -> now - 7L * 24 * 60 * 60 * 1000
                SearchTimeFilter.DAYS_30 -> now - 30L * 24 * 60 * 60 * 1000
            }
            return allEntries
                .asSequence()
                .filter { it.createdAtEpochMs >= recentFrom }
                .filter {
                    if (searchKeyword.isBlank()) {
                        true
                    } else {
                        it.content.contains(searchKeyword.trim(), ignoreCase = true)
                    }
                }
                .sortedByDescending { it.createdAtEpochMs }
                .toList()
        }

}

enum class SearchTimeFilter {
    ALL,
    DAYS_7,
    DAYS_30,
}

@HiltViewModel
class LifeStreamViewModel @Inject constructor(
    private val repository: LifeStreamRepository,
    private val settingsStore: MemorySettingsStore,
) : ViewModel() {
    companion object {
        private const val TAG = "MemoryLifeStreamVM"
    }
    private val _uiState = MutableStateFlow(LifeStreamUiState())
    val uiState: StateFlow<LifeStreamUiState> = _uiState.asStateFlow()
    private var hasBootstrappedFromLocal = false
    private var lastHomeEmptyRefreshAt = 0L

    init {
        MemoryLog.d(TAG, "init")
        observeSettings()
        observeLocalEntries()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsStore.showCloudBadge.collectLatest { enabled ->
                MemoryLog.d(TAG, "settings showCloudBadge=$enabled")
                _uiState.update { it.copy(showCloudBadge = enabled) }
            }
        }
        viewModelScope.launch {
            settingsStore.cloudSyncEnabled.collectLatest { enabled ->
                MemoryLog.d(TAG, "settings cloudSyncEnabled=$enabled")
                _uiState.update { it.copy(cloudSyncEnabled = enabled) }
            }
        }
    }

    private fun observeLocalEntries() {
        viewModelScope.launch {
            repository.observeEntries().collectLatest { entries ->
                MemoryLog.d(TAG, "observeEntries count=${entries.size}")
                _uiState.update {
                    it.copy(
                        allEntries = entries.sortedByDescending { e -> e.createdAtEpochMs },
                        pendingSyncCount = countPendingMedia(entries),
                    )
                }
                if (!hasBootstrappedFromLocal) {
                    hasBootstrappedFromLocal = true
                    MemoryLog.d(TAG, "bootstrap trigger hasLocal=${entries.isNotEmpty()}")
                    bootstrap(hasLocal = entries.isNotEmpty())
                }
            }
        }
    }

    private fun bootstrap(hasLocal: Boolean) {
        viewModelScope.launch {
            MemoryLog.d(TAG, "bootstrap start hasLocal=$hasLocal")
            _uiState.update { it.copy(isInitialLoading = !hasLocal) }
            runCatching { repository.getAll() }
                .onSuccess { MemoryLog.d(TAG, "bootstrap remote refresh success") }
                .onFailure {
                    MemoryLog.w(TAG, "bootstrap remote refresh failed", it)
                    _uiState.update {
                        state -> state.copy(
                            message = friendlyErrorMessage(it, "加载失败，请下拉重试"),
                            isError = true,
                        )
                    }
                }
            _uiState.update { it.copy(isInitialLoading = false) }
            MemoryLog.d(TAG, "bootstrap done")
        }
    }

    fun refresh() {
        viewModelScope.launch {
            MemoryLog.d(TAG, "refresh start")
            _uiState.update { it.copy(isBusy = true) }
            runCatching { repository.getAll() }
                .onSuccess { _ ->
                    MemoryLog.d(TAG, "refresh success")
                    _uiState.update { it.copy(isBusy = false, isInitialLoading = false) }
                }
                .onFailure {
                    MemoryLog.w(TAG, "refresh failed", it)
                    _uiState.update {
                        state -> state.copy(
                            isBusy = false,
                            isInitialLoading = false,
                            message = friendlyErrorMessage(it, "加载失败，请下拉重试"),
                            isError = true,
                        )
                    }
                }
        }
    }

    fun refreshOnHomeVisibleIfEmpty() {
        val snapshot = uiState.value
        if (snapshot.timelineEntries.isNotEmpty() || snapshot.isBusy || snapshot.isInitialLoading) return
        val now = System.currentTimeMillis()
        if (now - lastHomeEmptyRefreshAt < 1_500L) return
        lastHomeEmptyRefreshAt = now
        viewModelScope.launch {
            MemoryLog.d(TAG, "refreshOnHomeVisibleIfEmpty start")
            _uiState.update { it.copy(isInitialLoading = true) }
            runCatching { repository.getAll() }
                .onSuccess { MemoryLog.d(TAG, "refreshOnHomeVisibleIfEmpty success") }
                .onFailure {
                    MemoryLog.w(TAG, "refreshOnHomeVisibleIfEmpty failed", it)
                    _uiState.update {
                        it.copy(
                            message = "加载失败，请下拉重试",
                            isError = true,
                        )
                    }
                }
            _uiState.update { it.copy(isInitialLoading = false) }
        }
    }

    fun updateSearchKeyword(value: String) {
        _uiState.update { it.copy(searchKeyword = value) }
    }

    fun clearSearchKeyword() {
        _uiState.update { it.copy(searchKeyword = "") }
    }

    fun setSearchTimeFilter(filter: SearchTimeFilter) {
        _uiState.update { it.copy(searchTimeFilter = filter) }
    }

    fun openCreateRecordPage() {
        _uiState.update {
            it.copy(
                editingEntryId = null,
                draftContent = "",
                draftTimeText = nowInputTime(),
                draftMediaList = emptyList(),
                customTimeInput = nowInputTime(),
            )
        }
    }

    fun openEditRecordPage(entry: LifeEntry) {
        _uiState.update {
            it.copy(
                editingEntryId = entry.id,
                draftContent = entry.content,
                draftTimeText = formatDisplayTime(entry.createdAtEpochMs),
                draftMediaList = entry.mediaList,
                customTimeInput = formatDisplayTime(entry.createdAtEpochMs),
            )
        }
    }

    fun requestCloseRecordPage(): Boolean {
        val snapshot = uiState.value
        if (!hasDraftContent(snapshot)) {
            _uiState.update {
                it.copy(
                    editingEntryId = null,
                    draftContent = "",
                    draftMediaList = emptyList(),
                    recordCloseNonce = it.recordCloseNonce + 1,
                )
            }
            return true
        }
        _uiState.update { it.copy(isDiscardDialogVisible = true) }
        return false
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(isDiscardDialogVisible = false) }
    }

    fun discardAndCloseRecordPage() {
        _uiState.update {
            it.copy(
                isDiscardDialogVisible = false,
                editingEntryId = null,
                draftContent = "",
                draftMediaList = emptyList(),
                recordCloseNonce = it.recordCloseNonce + 1,
            )
        }
    }

    fun updateDraftContent(value: String) {
        _uiState.update { it.copy(draftContent = value) }
    }

    fun updateDraftTimeText(value: String) {
        _uiState.update { it.copy(draftTimeText = value) }
    }

    fun appendMedia(uri: String, mimeType: String?) {
        _uiState.update { it.copy(draftMediaList = it.draftMediaList + LifeMedia(uri = uri, mimeType = mimeType)) }
    }

    fun removeMediaAt(index: Int) {
        _uiState.update {
            if (index !in it.draftMediaList.indices) return@update it
            it.copy(draftMediaList = it.draftMediaList.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun openTimeDialog() {
        _uiState.update { it.copy(isTimeDialogVisible = true, customTimeInput = it.draftTimeText) }
    }

    fun dismissTimeDialog() {
        _uiState.update { it.copy(isTimeDialogVisible = false) }
    }

    fun useCurrentTime() {
        val now = nowInputTime()
        _uiState.update { it.copy(draftTimeText = now, customTimeInput = now, isTimeDialogVisible = false) }
    }

    fun updateCustomTimeInput(value: String) {
        _uiState.update { it.copy(customTimeInput = value) }
    }

    fun applyCustomTime() {
        val value = uiState.value.customTimeInput.trim()
        if (parseInputTime(value) == null) {
            emitError("时间格式错误，请用 yyyy-MM-dd HH:mm")
            return
        }
        _uiState.update { it.copy(draftTimeText = value, isTimeDialogVisible = false) }
    }

    fun saveRecord() {
        val snapshot = uiState.value
        val content = snapshot.draftContent.trim()
        if (content.isBlank() && snapshot.draftMediaList.isEmpty()) {
            _uiState.update {
                it.copy(
                    editingEntryId = null,
                    recordCloseNonce = it.recordCloseNonce + 1,
                )
            }
            return
        }
        val epochMs = parseInputTime(snapshot.draftTimeText)
            ?: run {
                emitError("时间格式错误，请用 yyyy-MM-dd HH:mm")
                return
            }
        val type = when {
            snapshot.draftMediaList.any { it.mimeType?.startsWith("video/") == true } -> LifeEntryType.VIDEO
            snapshot.draftMediaList.any { it.mimeType?.startsWith("image/") == true } -> LifeEntryType.IMAGE
            else -> LifeEntryType.TEXT
        }

        viewModelScope.launch {
            val entryId = snapshot.editingEntryId ?: UUID.randomUUID().toString()
            _uiState.update { it.copy(isSavingRecord = true) }
            runCatching {
                MemoryLog.d(TAG, "saveRecord id=${entryId.take(8)} edit=${snapshot.editingEntryId != null} media=${snapshot.draftMediaList.size}")
                val entry = LifeEntry(
                    id = entryId,
                    content = content,
                    createdAtEpochMs = epochMs,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncState = SyncState.LOCAL_ONLY,
                    type = type,
                    mediaList = snapshot.draftMediaList,
                )
                repository.upsert(entry)
                MemoryLog.d(TAG, "saveRecord done id=${entryId.take(8)}")
                _uiState.update {
                    it.copy(
                        editingEntryId = null,
                        isDiscardDialogVisible = false,
                        recordCloseNonce = it.recordCloseNonce + 1,
                        scrollToTopNonce = it.scrollToTopNonce + 1,
                        message = if (it.pendingSyncCount > 0) {
                            "已本地保存，网络恢复后自动同步"
                        } else {
                            if (snapshot.editingEntryId == null) "已记录" else "已更新"
                        },
                        isError = false,
                    )
                }
            }.onFailure {
                MemoryLog.w(TAG, "saveRecord failed id=${entryId.take(8)}", it)
                emitError(friendlyErrorMessage(it, "保存失败，请稍后重试"))
            }
            _uiState.update { it.copy(isSavingRecord = false) }
        }
    }

    fun deleteEntry(entry: LifeEntry) {
        viewModelScope.launch {
            MemoryLog.d(TAG, "deleteEntry id=${entry.id.take(8)}")
            repository.delete(entry.id)
            _uiState.update {
                it.copy(
                    message = "已删除",
                    isError = false,
                )
            }
        }
    }

    fun exportAsJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.exportToJson() }
                .onSuccess {
                    onResult(it)
                    _uiState.update { state -> state.copy(message = "导出内容已生成", isError = false) }
                }
                .onFailure { emitError(it.message ?: "导出失败") }
        }
    }

    fun exportSingleEntryAsJson(entry: LifeEntry, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { SINGLE_ENTRY_EXPORT_JSON.encodeToString(listOf(entry)) }
                .onSuccess(onResult)
                .onFailure { emitError(it.message ?: "导出失败") }
        }
    }

    fun importFromJson(json: String) {
        viewModelScope.launch {
            runCatching { repository.importFromJson(json) }
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(
                            message = "导入成功，共 $count 条",
                            isError = false,
                        )
                    }
                }
                .onFailure { emitError(it.message ?: "导入失败，请检查 JSON 格式") }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            MemoryLog.d(TAG, "syncNow click")
            if (!uiState.value.cloudSyncEnabled) {
                _uiState.update {
                    it.copy(
                        message = "云同步未开启，可在设置中打开",
                        isError = true,
                    )
                }
                return@launch
            }
            val pendingBefore = uiState.value.pendingSyncCount
            runCatching { repository.syncPendingMedia() }
                .onSuccess { changed ->
                    val pendingAfter = uiState.value.pendingSyncCount
                    MemoryLog.d(TAG, "syncNow done changed=$changed before=$pendingBefore after=$pendingAfter")
                    _uiState.update {
                        it.copy(
                            pendingSyncCount = pendingAfter,
                            message = when {
                                pendingBefore <= 0 -> "没有需要同步的内容"
                                changed > 0 || pendingAfter < pendingBefore -> "已同步 ${pendingBefore - pendingAfter} 条记录"
                                else -> "仍有 $pendingAfter 条待同步，请稍后重试"
                            },
                            isError = pendingBefore > 0 && changed <= 0 && pendingAfter >= pendingBefore,
                        )
                    }
                }
                .onFailure {
                    MemoryLog.w(TAG, "syncNow failed", it)
                    emitError(friendlyErrorMessage(it, "同步失败，请稍后重试"))
                }
        }
    }

    fun syncSingleEntry(entryId: String) {
        viewModelScope.launch {
            MemoryLog.d(TAG, "syncSingleEntry id=${entryId.take(8)}")
            if (!uiState.value.cloudSyncEnabled) {
                _uiState.update {
                    it.copy(
                        message = "云同步未开启，可在设置中打开",
                        isError = true,
                    )
                }
                return@launch
            }
            runCatching { repository.syncEntry(entryId) }
                .onSuccess { synced ->
                    MemoryLog.d(TAG, "syncSingleEntry done id=${entryId.take(8)} synced=$synced")
                    _uiState.update {
                        it.copy(
                            message = if (synced) "该条记录已同步到云端" else "该条记录同步失败，请稍后重试",
                            isError = !synced,
                        )
                    }
                }
                .onFailure {
                    MemoryLog.w(TAG, "syncSingleEntry failed id=${entryId.take(8)}", it)
                    emitError(friendlyErrorMessage(it, "同步失败，请稍后重试"))
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    fun setShowCloudBadge(enabled: Boolean) {
        settingsStore.setShowCloudBadge(enabled)
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        settingsStore.setCloudSyncEnabled(enabled)
        if (enabled) {
            _uiState.update {
                it.copy(
                    message = "已开启云同步。请先注册并登录 cloud 项目账号后使用",
                    isError = false,
                )
            }
        }
    }

    private fun emitError(message: String) {
        _uiState.update { it.copy(message = message, isError = true) }
    }

    private fun friendlyErrorMessage(error: Throwable, fallback: String): String {
        val message = error.message.orEmpty().lowercase()
        val unreachable = error is UnknownHostException ||
            message.contains("unable to resolve host") ||
            message.contains("unknownhost")
        return if (unreachable) "网络不可达，请稍候重试" else fallback
    }

}

private fun hasDraftContent(state: LifeStreamUiState): Boolean =
    state.draftContent.isNotBlank() || state.draftMediaList.isNotEmpty()

private fun countPendingMedia(entries: List<LifeEntry>): Int =
    entries.count { entry ->
        entry.syncState != SyncState.SYNCED
    }

private fun parseInputTime(text: String): Long? =
    runCatching {
        LocalDateTime.parse(text.trim(), INPUT_TIME_FORMATTER)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

private fun nowInputTime(): String =
    INPUT_TIME_FORMATTER.format(LocalDateTime.now())

fun formatDisplayTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime())
