package cn.verlu.doctor.presentation.herb.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.doctor.data.herb.HerbRepository
import cn.verlu.doctor.data.herb.dto.ArticleMeta
import cn.verlu.doctor.presentation.herb.HerbCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HerbSearchUiState(
    val query: String = "",
    val results: List<ArticleMeta> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HerbSearchViewModel @Inject constructor(
    private val repository: HerbRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HerbSearchUiState())
    val state: StateFlow<HerbSearchUiState> = _state.asStateFlow()

    val favoritePaths: StateFlow<Set<String>> =
        repository.observeFavoritePathSet()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList(), error = null, isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(isLoading = true, error = null) }
            val r = repository.searchAndPersist(q.trim(), HerbCollection.All.param)
            r.fold(
                onSuccess = { list ->
                    _state.update { it.copy(results = list, isLoading = false, error = null) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.herbUserVisibleError()) }
                },
            )
        }
    }

    fun toggleFavorite(meta: ArticleMeta) {
        viewModelScope.launch {
            repository.toggleFavorite(meta.path, meta.title, meta.collection, meta.serial)
        }
    }

}
