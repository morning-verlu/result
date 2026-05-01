package cn.verlu.lulu.presentation.memory

import androidx.lifecycle.viewModelScope
import cn.verlu.lulu.core.mvi.MviViewModel
import cn.verlu.lulu.domain.memory.MemoryRepository
import cn.verlu.lulu.domain.memory.MemoryType
import cn.verlu.lulu.feature.lifestream.data.local.MemorySettingsStore
import cn.verlu.lulu.presentation.memory.MemoryContract.Effect
import cn.verlu.lulu.presentation.memory.MemoryContract.Intent
import cn.verlu.lulu.presentation.memory.MemoryContract.MemoryTimeFilter
import cn.verlu.lulu.presentation.memory.MemoryContract.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val memorySettingsStore: MemorySettingsStore,
) : MviViewModel<UiState, Intent, Effect>(UiState()) {
    private var loadJob: Job? = null
    private var detailJob: Job? = null

    init {
        onIntent(Intent.Load)
        observeSyncSummary()
        observeSettings()
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.Load, Intent.Retry -> loadMemories()
            Intent.Refresh -> refreshMemories()

            Intent.StartCreate -> reduce {
                it.copy(
                    draftTitle = "",
                    draftContent = "",
                    draftType = MemoryType.Moment,
                    draftTags = "",
                    draftMood = "",
                    draftScene = "",
                    isSaving = false,
                    errorMessage = null,
                    isEditing = false,
                )
            }

            Intent.CancelCreate -> reduce {
                it.copy(
                    draftTitle = "",
                    draftContent = "",
                    draftType = MemoryType.Moment,
                    draftTags = "",
                    draftMood = "",
                    draftScene = "",
                    isSaving = false,
                )
            }

            is Intent.TitleChanged -> reduce { it.copy(draftTitle = intent.value) }
            is Intent.ContentChanged -> reduce { it.copy(draftContent = intent.value) }
            is Intent.TypeChanged -> reduce { it.copy(draftType = intent.value) }
            is Intent.TagsChanged -> reduce { it.copy(draftTags = intent.value) }
            is Intent.MoodChanged -> reduce { it.copy(draftMood = intent.value) }
            is Intent.SceneChanged -> reduce { it.copy(draftScene = intent.value) }
            is Intent.SearchChanged -> reduce { applyFilters(it.copy(searchQuery = intent.value)) }
            is Intent.TypeFilterChanged -> reduce { applyFilters(it.copy(typeFilter = intent.value)) }
            is Intent.TimeFilterChanged -> reduce { applyFilters(it.copy(timeFilter = intent.value)) }
            Intent.ClearFilters -> reduce {
                applyFilters(
                    it.copy(
                        searchQuery = "",
                        typeFilter = null,
                        timeFilter = MemoryTimeFilter.All,
                    )
                )
            }
            Intent.SaveMemory -> saveMemory()
            Intent.SaveSelectedMemory -> updateSelectedMemory()
            is Intent.LoadDetail -> loadDetail(intent.id)
            Intent.StartEdit -> startEdit()
            Intent.CancelEdit -> cancelEdit()
            Intent.DeleteSelectedMemory -> deleteSelectedMemory()
            Intent.RetrySync -> retrySync()
            Intent.ClearError -> reduce { it.copy(errorMessage = null) }
        }
    }

    private fun loadMemories() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            reduce { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                delay(250)
                memoryRepository.observeEntries().collectLatest { memories ->
                    reduce {
                        it.copy(
                            allMemories = memories,
                            memories = applyFilters(
                                memories = memories,
                                searchQuery = it.searchQuery,
                                typeFilter = it.typeFilter,
                                timeFilter = it.timeFilter,
                            ),
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null,
                        )
                    }
                }
            }.onFailure { throwable ->
                reduce {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = throwable.message ?: "记忆加载失败",
                    )
                }
            }
        }
    }

    private fun refreshMemories() {
        viewModelScope.launch {
            reduce { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching {
                memoryRepository.syncNow()
            }.onFailure { throwable ->
                emitEffect(Effect.ShowMessage(throwable.message ?: "同步失败"))
            }
            reduce { it.copy(isRefreshing = false) }
        }
    }

    private fun observeSyncSummary() {
        viewModelScope.launch {
            memoryRepository.observeSyncSummary().collectLatest { summary ->
                reduce { it.copy(syncSummary = summary) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            memorySettingsStore.showCloudBadge.collectLatest { enabled ->
                reduce { it.copy(showCloudBadge = enabled) }
            }
        }
    }

    private fun retrySync() {
        viewModelScope.launch {
            runCatching {
                memoryRepository.retryFailed()
            }.onSuccess {
                emitEffect(Effect.ShowMessage("已发起同步"))
            }.onFailure { throwable ->
                emitEffect(Effect.ShowMessage(throwable.message ?: "同步失败"))
            }
        }
    }

    private fun saveMemory() {
        val state = currentState()
        if (!state.canSave) {
            viewModelScope.launch {
                emitEffect(Effect.ShowMessage("先写点内容"))
            }
            return
        }

        viewModelScope.launch {
            reduce { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                val content = state.draftContent.trim()
                memoryRepository.createEntry(
                    title = state.draftTitle.toMemoryTitle(content),
                    content = content,
                    type = state.draftType,
                    tags = state.draftTags.toTags(),
                    mood = state.draftMood,
                    scene = state.draftScene,
                )
            }.onSuccess {
                reduce {
                    it.copy(
                        draftTitle = "",
                        draftContent = "",
                        draftType = MemoryType.Moment,
                        draftTags = "",
                        draftMood = "",
                        draftScene = "",
                        isSaving = false,
                    )
                }
                emitEffect(Effect.ShowMessage("记忆已保存"))
                emitEffect(Effect.MemorySaved)
            }.onFailure { throwable ->
                reduce {
                    it.copy(
                        isSaving = false,
                    )
                }
                emitEffect(Effect.ShowMessage(throwable.message ?: "保存失败"))
            }
        }
    }

    private fun updateSelectedMemory() {
        val state = currentState()
        val memory = state.selectedMemory ?: return
        if (!state.canSave) {
            viewModelScope.launch {
                emitEffect(Effect.ShowMessage("先写点内容"))
            }
            return
        }

        viewModelScope.launch {
            reduce { it.copy(isSaving = true, detailErrorMessage = null) }
            runCatching {
                val content = state.draftContent.trim()
                memoryRepository.updateEntry(
                    id = memory.id,
                    title = state.draftTitle.toMemoryTitle(content),
                    content = content,
                    type = state.draftType,
                    tags = state.draftTags.toTags(),
                    mood = state.draftMood,
                    scene = state.draftScene,
                )
            }.onSuccess {
                reduce { it.copy(isSaving = false, isEditing = false) }
                emitEffect(Effect.ShowMessage("记忆已更新"))
            }.onFailure { throwable ->
                reduce { it.copy(isSaving = false) }
                emitEffect(Effect.ShowMessage(throwable.message ?: "更新失败"))
            }
        }
    }

    private fun loadDetail(id: String) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            reduce {
                it.copy(
                    selectedMemory = null,
                    isDetailLoading = true,
                    detailErrorMessage = null,
                    isDeleting = false,
                    isEditing = false,
                )
            }
            runCatching {
                memoryRepository.observeEntry(id).collectLatest { memory ->
                    reduce {
                        it.copy(
                            selectedMemory = memory,
                            isDetailLoading = false,
                            detailErrorMessage = if (memory == null) "记忆不存在或已删除" else null,
                        )
                    }
                }
            }.onFailure { throwable ->
                reduce {
                    it.copy(
                        isDetailLoading = false,
                        detailErrorMessage = throwable.message ?: "记忆加载失败",
                    )
                }
            }
        }
    }

    private fun startEdit() {
        val memory = currentState().selectedMemory ?: return
        reduce {
            it.copy(
                isEditing = true,
                draftTitle = memory.title,
                draftContent = memory.content,
                draftType = memory.type,
                draftTags = memory.tags.joinToString(", "),
                draftMood = memory.mood,
                draftScene = memory.scene,
                isSaving = false,
            )
        }
    }

    private fun cancelEdit() {
        reduce {
            it.copy(
                isEditing = false,
                draftTitle = "",
                draftContent = "",
                draftType = MemoryType.Moment,
                draftTags = "",
                draftMood = "",
                draftScene = "",
                isSaving = false,
            )
        }
    }

    private fun deleteSelectedMemory() {
        val memory = currentState().selectedMemory ?: return
        viewModelScope.launch {
            reduce { it.copy(isDeleting = true) }
            runCatching {
                memoryRepository.deleteEntry(memory.id)
            }.onSuccess {
                reduce {
                    it.copy(
                        selectedMemory = null,
                        isDeleting = false,
                    )
                }
                emitEffect(Effect.ShowMessage("记忆已删除"))
                emitEffect(Effect.MemoryDeleted)
            }.onFailure { throwable ->
                reduce { it.copy(isDeleting = false) }
                emitEffect(Effect.ShowMessage(throwable.message ?: "删除失败"))
            }
        }
    }

    private fun String.toTags(): List<String> =
        split(",", "，", " ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun String.toMemoryTitle(content: String): String =
        trim().ifBlank {
            content.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(28)
                ?: "未命名记忆"
        }

    private fun applyFilters(state: UiState): UiState =
        state.copy(
            memories = applyFilters(
                memories = state.allMemories,
                searchQuery = state.searchQuery,
                typeFilter = state.typeFilter,
                timeFilter = state.timeFilter,
            )
        )

    private fun applyFilters(
        memories: List<cn.verlu.lulu.domain.memory.Memory>,
        searchQuery: String,
        typeFilter: MemoryType?,
        timeFilter: MemoryTimeFilter,
    ): List<cn.verlu.lulu.domain.memory.Memory> {
        val keyword = searchQuery.trim()
        return memories.filter { memory ->
            val typeMatches = typeFilter == null || memory.type == typeFilter
            val timeMatches = memory.createdAt.matches(timeFilter)
            val keywordMatches = keyword.isBlank() ||
                memory.title.contains(keyword, ignoreCase = true) ||
                memory.content.contains(keyword, ignoreCase = true) ||
                memory.mood.contains(keyword, ignoreCase = true) ||
                memory.scene.contains(keyword, ignoreCase = true) ||
                memory.tags.any { it.contains(keyword, ignoreCase = true) }
            typeMatches && timeMatches && keywordMatches
        }
    }

    private fun Instant.matches(filter: MemoryTimeFilter): Boolean {
        if (filter == MemoryTimeFilter.All) return true
        val zone = ZoneId.systemDefault()
        val date = atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (filter) {
            MemoryTimeFilter.All -> true
            MemoryTimeFilter.Today -> date == today
            MemoryTimeFilter.ThisWeek -> !date.isBefore(today.minusDays(6))
            MemoryTimeFilter.ThisMonth -> date.year == today.year && date.month == today.month
        }
    }
}
