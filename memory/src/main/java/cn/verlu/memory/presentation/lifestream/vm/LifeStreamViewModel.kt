package cn.verlu.memory.presentation.lifestream.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.memory.domain.model.LifeEntry
import cn.verlu.memory.domain.model.LifeMedia
import cn.verlu.memory.domain.model.LifeEntryType
import cn.verlu.memory.domain.repository.LifeStreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val INPUT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val SINGLE_ENTRY_EXPORT_JSON = Json { prettyPrint = true }

data class LifeStreamUiState(
    val allEntries: List<LifeEntry> = emptyList(),
    val isProfilePageOpen: Boolean = false,
    val isSearchPageOpen: Boolean = false,
    val detailEntryId: String? = null,
    val searchKeyword: String = "",
    val searchTimeFilter: SearchTimeFilter = SearchTimeFilter.ALL,
    val isRecordPageOpen: Boolean = false,
    val editingEntryId: String? = null,
    val draftContent: String = "",
    val draftTimeText: String = nowInputTime(),
    val draftMediaList: List<LifeMedia> = emptyList(),
    val isDiscardDialogVisible: Boolean = false,
    val isTimeDialogVisible: Boolean = false,
    val customTimeInput: String = nowInputTime(),
    val scrollToTopNonce: Int = 0,
    val pendingSyncCount: Int = 0,
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

    val detailEntry: LifeEntry?
        get() = detailEntryId?.let { id -> allEntries.firstOrNull { it.id == id } }
}

enum class SearchTimeFilter {
    ALL,
    DAYS_7,
    DAYS_30,
}

@HiltViewModel
class LifeStreamViewModel @Inject constructor(
    private val repository: LifeStreamRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LifeStreamUiState())
    val uiState: StateFlow<LifeStreamUiState> = _uiState.asStateFlow()

    init {
        refresh()
        startAutoSyncLoop()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val entries = repository.getAll()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    allEntries = entries.sortedByDescending { e -> e.createdAtEpochMs },
                    pendingSyncCount = countPendingMedia(entries),
                )
            }
        }
    }

    fun updateSearchKeyword(value: String) {
        _uiState.update { it.copy(searchKeyword = value) }
    }

    fun openSearchPage() {
        _uiState.update { it.copy(isSearchPageOpen = true) }
    }

    fun closeSearchPage() {
        _uiState.update { it.copy(isSearchPageOpen = false) }
    }

    fun openProfilePage() {
        _uiState.update { it.copy(isProfilePageOpen = true) }
    }

    fun closeProfilePage() {
        _uiState.update { it.copy(isProfilePageOpen = false) }
    }

    fun openDetailPage(entry: LifeEntry) {
        _uiState.update { it.copy(detailEntryId = entry.id) }
    }

    fun closeDetailPage() {
        _uiState.update { it.copy(detailEntryId = null) }
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
                isRecordPageOpen = true,
                detailEntryId = null,
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
                isRecordPageOpen = true,
                detailEntryId = null,
                editingEntryId = entry.id,
                draftContent = entry.content,
                draftTimeText = formatDisplayTime(entry.createdAtEpochMs),
                draftMediaList = entry.mediaList,
                customTimeInput = formatDisplayTime(entry.createdAtEpochMs),
            )
        }
    }

    fun requestCloseRecordPage() {
        val snapshot = uiState.value
        if (!hasDraftContent(snapshot)) {
            _uiState.update { it.copy(isRecordPageOpen = false, editingEntryId = null) }
            return
        }
        _uiState.update { it.copy(isDiscardDialogVisible = true) }
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(isDiscardDialogVisible = false) }
    }

    fun discardAndCloseRecordPage() {
        _uiState.update {
            it.copy(
                isDiscardDialogVisible = false,
                isRecordPageOpen = false,
                editingEntryId = null,
                draftContent = "",
                draftMediaList = emptyList(),
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
            _uiState.update { it.copy(isRecordPageOpen = false, editingEntryId = null) }
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
            val entry = LifeEntry(
                id = entryId,
                content = content,
                createdAtEpochMs = epochMs,
                type = type,
                mediaList = snapshot.draftMediaList,
            )
            repository.upsert(entry)
            val entries = repository.getAll()
            _uiState.update {
                it.copy(
                    allEntries = entries.sortedByDescending { e -> e.createdAtEpochMs },
                    isRecordPageOpen = false,
                    editingEntryId = null,
                    isDiscardDialogVisible = false,
                    scrollToTopNonce = it.scrollToTopNonce + 1,
                    message = if (countPendingMedia(entries) > 0) {
                        "已本地保存，网络恢复后自动同步"
                    } else {
                        if (snapshot.editingEntryId == null) "已记录" else "已更新"
                    },
                    pendingSyncCount = countPendingMedia(entries),
                    isError = false,
                )
            }
        }
    }

    fun deleteEntry(entry: LifeEntry) {
        viewModelScope.launch {
            repository.delete(entry.id)
            val entries = repository.getAll()
            _uiState.update {
                it.copy(
                    allEntries = entries.sortedByDescending { e -> e.createdAtEpochMs },
                    detailEntryId = if (it.detailEntryId == entry.id) null else it.detailEntryId,
                    message = "已删除",
                    pendingSyncCount = countPendingMedia(entries),
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
                    val entries = repository.getAll()
                    _uiState.update {
                        it.copy(
                            allEntries = entries.sortedByDescending { e -> e.createdAtEpochMs },
                            message = "导入成功，共 $count 条",
                            pendingSyncCount = countPendingMedia(entries),
                            isError = false,
                        )
                    }
                }
                .onFailure { emitError(it.message ?: "导入失败，请检查 JSON 格式") }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val pendingBefore = uiState.value.pendingSyncCount
            runCatching { repository.syncPendingMedia() }
                .onSuccess { changed ->
                    val entries = repository.getAll()
                    val pendingAfter = countPendingMedia(entries)
                    _uiState.update {
                        it.copy(
                            allEntries = entries.sortedByDescending { e -> e.createdAtEpochMs },
                            pendingSyncCount = pendingAfter,
                            message = when {
                                pendingBefore <= 0 -> "没有需要同步的内容"
                                changed > 0 || pendingAfter < pendingBefore -> "已同步 ${pendingBefore - pendingAfter} 条媒体"
                                else -> "仍有 $pendingAfter 项待同步，请稍后重试"
                            },
                            isError = pendingBefore > 0 && changed <= 0 && pendingAfter >= pendingBefore,
                        )
                    }
                }
                .onFailure { emitError(it.message ?: "同步失败，请稍后重试") }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    private fun emitError(message: String) {
        _uiState.update { it.copy(message = message, isError = true) }
    }

    private fun startAutoSyncLoop() {
        viewModelScope.launch {
            while (isActive) {
                runCatching { repository.syncPendingMedia() }
                    .onSuccess { changed ->
                        if (changed > 0) {
                            val entries = repository.getAll()
                            _uiState.update {
                                it.copy(
                                    allEntries = entries.sortedByDescending { e -> e.createdAtEpochMs },
                                    pendingSyncCount = countPendingMedia(entries),
                                    message = "已自动同步 $changed 条待同步媒体",
                                    isError = false,
                                )
                            }
                        }
                    }
                delay(20_000)
            }
        }
    }
}

private fun hasDraftContent(state: LifeStreamUiState): Boolean =
    state.draftContent.isNotBlank() || state.draftMediaList.isNotEmpty()

private fun countPendingMedia(entries: List<LifeEntry>): Int =
    entries.sumOf { entry ->
        entry.mediaList.count { media ->
            !isCloudSyncedMediaUrl(media.uri)
        }
    }

private fun isCloudSyncedMediaUrl(uri: String): Boolean {
    return uri.startsWith("http://") || uri.startsWith("https://")
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
