package cn.verlu.doctor.presentation.herb.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.doctor.data.herb.HerbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 挂在「本草主页」路由下，供首页/目录/搜索等列表共享收藏状态与切换。
 */
@HiltViewModel
class HerbSharedFavoritesViewModel @Inject constructor(
    private val repository: HerbRepository,
) : ViewModel() {

    val favoritePaths: StateFlow<Set<String>> =
        repository.observeFavoritePathSet()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFavorite(
        path: String,
        title: String,
        collection: String,
        serial: Int?,
    ) {
        viewModelScope.launch {
            repository.toggleFavorite(path, title, collection, serial)
        }
    }
}
