package cn.verlu.doctor.presentation.herb.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.doctor.data.herb.HerbRepository
import cn.verlu.doctor.data.local.herb.HerbFavoriteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HerbFavoritesListViewModel @Inject constructor(
    private val repository: HerbRepository,
) : ViewModel() {

    val favorites: StateFlow<List<HerbFavoriteEntity>> =
        repository.observeFavorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(path: String) {
        viewModelScope.launch {
            repository.removeFavorite(path)
        }
    }

    fun restoreFavorite(entity: HerbFavoriteEntity) {
        viewModelScope.launch {
            repository.restoreFavorite(entity)
        }
    }
}
