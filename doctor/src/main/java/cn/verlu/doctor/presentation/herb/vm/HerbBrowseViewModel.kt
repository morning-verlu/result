package cn.verlu.doctor.presentation.herb.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.doctor.data.herb.HerbRepository
import cn.verlu.doctor.data.herb.dto.ArticleMeta
import cn.verlu.doctor.presentation.herb.HerbCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HerbBrowseUiState(
    val collection: HerbCollection = HerbCollection.All,
    val items: List<ArticleMeta> = emptyList(),
    val offset: Int = 0,
    val pageSize: Int = 30,
    val hasMore: Boolean = true,
    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val error: String? = null,
    val total: Int? = null,
)

@HiltViewModel
class HerbBrowseViewModel @Inject constructor(
    private val repository: HerbRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(HerbBrowseUiState())
    val state: StateFlow<HerbBrowseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            loadFromCacheThenNetworkIfEmpty()
        }
    }

    private suspend fun loadFromCacheOnly() {
        val col = _state.value.collection.param
        val c = repository.loadBrowseFromCache(col) ?: return
        _state.update {
            it.copy(
                items = c.items,
                offset = c.nextOffset,
                hasMore = c.hasMore,
                total = c.total,
                error = null,
            )
        }
    }

    /** 先读缓存；若从未同步过该卷（无缓存行）则自动拉第一页。仅有空列表时依赖下拉刷新，避免反复请求。 */
    private suspend fun loadFromCacheThenNetworkIfEmpty() {
        supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        loadFromCacheOnly()
        val col = _state.value.collection.param
        val cached = repository.loadBrowseFromCache(col)
        if (cached == null) {
            refresh()
        }
    }

    fun setCollection(c: HerbCollection) {
        _state.update {
            it.copy(
                collection = c,
                items = emptyList(),
                offset = 0,
                hasMore = true,
                total = null,
                error = null,
            )
        }
        viewModelScope.launch { loadFromCacheThenNetworkIfEmpty() }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            val r = repository.syncBrowseFirstPage(_state.value.collection.param, _state.value.pageSize)
            r.fold(
                onSuccess = { bc ->
                    _state.update {
                        it.copy(
                            items = bc.items,
                            offset = bc.nextOffset,
                            hasMore = bc.hasMore,
                            total = bc.total,
                            isRefreshing = false,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    Log.e("Doctor/HerbBrowseVM", "refresh failed", e)
                    _state.update {
                        it.copy(isRefreshing = false)
                    }
                },
            )
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.isAppending || s.isRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(isAppending = true, error = null) }
            val r = repository.syncBrowseMore(s.collection.param, s.pageSize)
            r.fold(
                onSuccess = { bc ->
                    _state.update {
                        it.copy(
                            items = bc.items,
                            offset = bc.nextOffset,
                            hasMore = bc.hasMore,
                            isAppending = false,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    Log.e("Doctor/HerbBrowseVM", "loadMore failed", e)
                    _state.update {
                        it.copy(isAppending = false)
                    }
                },
            )
        }
    }
}
