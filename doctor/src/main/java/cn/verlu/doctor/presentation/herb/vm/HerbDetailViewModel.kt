package cn.verlu.doctor.presentation.herb.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.doctor.data.herb.HerbRepository
import cn.verlu.doctor.data.herb.dto.ArticleDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HerbDetailUiState(
    val path: String = "",
    val article: ArticleDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HerbDetailViewModel @Inject constructor(
    private val repository: HerbRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(HerbDetailUiState())
    val state: StateFlow<HerbDetailUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = combine(
        state.map { it.path },
        repository.observeFavoritePathSet(),
    ) { path, set ->
        path.isNotBlank() && path in set
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 有 Room 缓存则只读本地，不再请求网络；无缓存再拉取并写入 [HerbRepository.article]。
     */
    fun load(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch {
            val cached = repository.getArticleCached(path)
            if (cached != null) {
                _state.value = HerbDetailUiState(
                    path = path,
                    article = cached,
                    isLoading = false,
                    error = null,
                )
                return@launch
            }
            _state.value = HerbDetailUiState(path = path, article = null, isLoading = true, error = null)
            supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
            val r = repository.article(path)
            r.fold(
                onSuccess = { a ->
                    _state.update { it.copy(article = a, isLoading = false, error = null) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.herbUserVisibleError()) }
                },
            )
        }
    }

    fun toggleFavorite() {
        val a = _state.value.article ?: return
        viewModelScope.launch {
            repository.toggleFavorite(a.path, a.title, a.collection, a.serial)
        }
    }
}
