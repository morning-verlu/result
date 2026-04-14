package cn.verlu.doctor.presentation.herb.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.doctor.data.herb.HerbRepository
import cn.verlu.doctor.data.herb.dto.ArticlePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HerbHomeUiState(
    val spotlight: ArticlePreview? = null,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HerbHomeViewModel @Inject constructor(
    private val repository: HerbRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(HerbHomeUiState())
    val state: StateFlow<HerbHomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            loadSpotlightCache()
            // 等 Supabase 会话从存储恢复后再请求网络，避免误报「未登录」
            supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
            if (_state.value.spotlight == null) {
                ensureSpotlightFromNetwork()
            }
        }
    }

    private suspend fun loadSpotlightCache() {
        val one = repository.loadSpotlightFromCache()
        _state.update { it.copy(spotlight = one, error = null) }
    }

    /**
     * 首页进入时无缓存则自动向网络拉取；失败时有限次重试，避免只显示「暂无」而需用户手动下拉。
     */
    private suspend fun ensureSpotlightFromNetwork() {
        if (_state.value.spotlight != null) return
        _state.update { it.copy(isRefreshing = true, error = null) }
        repeat(SpotlightAutoRetry) { attempt ->
            if (refreshSpotlightOnce()) {
                _state.update { it.copy(isRefreshing = false) }
                return
            }
            if (attempt < SpotlightAutoRetry - 1) {
                delay(SpotlightRetryDelayMs)
            }
        }
        _state.update { it.copy(isRefreshing = false) }
    }

    /** @return 是否已成功写入 spotlight */
    private suspend fun refreshSpotlightOnce(): Boolean {
        val r = repository.refreshSpotlightFromNetwork()
        return r.fold(
            onSuccess = { one ->
                _state.update { it.copy(spotlight = one, error = null) }
                true
            },
            onFailure = { e ->
                Log.e("Doctor/HerbHomeVM", "refreshSpotlight failed", e)
                false
            },
        )
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            refreshSpotlightOnce()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    private companion object {
        const val SpotlightAutoRetry = 5
        const val SpotlightRetryDelayMs = 700L
    }
}
