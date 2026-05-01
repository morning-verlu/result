package cn.verlu.lulu.presentation.memory

import cn.verlu.lulu.domain.memory.Memory
import cn.verlu.lulu.domain.memory.MemorySyncSummary
import cn.verlu.lulu.domain.memory.MemoryType

object MemoryContract {
    data class UiState(
        val allMemories: List<Memory> = emptyList(),
        val memories: List<Memory> = emptyList(),
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
        val searchQuery: String = "",
        val typeFilter: MemoryType? = null,
        val timeFilter: MemoryTimeFilter = MemoryTimeFilter.All,
        val draftTitle: String = "",
        val draftContent: String = "",
        val draftType: MemoryType = MemoryType.Moment,
        val draftTags: String = "",
        val draftMood: String = "",
        val draftScene: String = "",
        val isSaving: Boolean = false,
        val selectedMemory: Memory? = null,
        val isDetailLoading: Boolean = false,
        val detailErrorMessage: String? = null,
        val isDeleting: Boolean = false,
        val isEditing: Boolean = false,
        val syncSummary: MemorySyncSummary = MemorySyncSummary(),
        val showCloudBadge: Boolean = true,
    ) {
        val canSave: Boolean
            get() = draftContent.isNotBlank() && !isSaving

        val hasActiveFilters: Boolean
            get() = searchQuery.isNotBlank() || typeFilter != null || timeFilter != MemoryTimeFilter.All
    }

    sealed interface Intent {
        data object Load : Intent
        data object Refresh : Intent
        data object Retry : Intent
        data object StartCreate : Intent
        data object CancelCreate : Intent
        data class LoadDetail(val id: String) : Intent
        data object StartEdit : Intent
        data object CancelEdit : Intent
        data class TitleChanged(val value: String) : Intent
        data class ContentChanged(val value: String) : Intent
        data class TypeChanged(val value: MemoryType) : Intent
        data class TagsChanged(val value: String) : Intent
        data class MoodChanged(val value: String) : Intent
        data class SceneChanged(val value: String) : Intent
        data class SearchChanged(val value: String) : Intent
        data class TypeFilterChanged(val value: MemoryType?) : Intent
        data class TimeFilterChanged(val value: MemoryTimeFilter) : Intent
        data object ClearFilters : Intent
        data object SaveMemory : Intent
        data object SaveSelectedMemory : Intent
        data object DeleteSelectedMemory : Intent
        data object RetrySync : Intent
        data object ClearError : Intent
    }

    sealed interface Effect {
        data class ShowMessage(val message: String) : Effect
        data object MemorySaved : Effect
        data object MemoryDeleted : Effect
    }

    enum class MemoryTimeFilter {
        All,
        Today,
        ThisWeek,
        ThisMonth,
    }
}
