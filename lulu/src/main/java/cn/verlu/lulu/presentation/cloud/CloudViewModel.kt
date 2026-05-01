package cn.verlu.lulu.presentation.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.lulu.domain.cloud.CloudRepository
import cn.verlu.lulu.presentation.cloud.CloudContract.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val cloudRepository: CloudRepository,
) : ViewModel() {
    val state: StateFlow<UiState> =
        combine(
            cloudRepository.observeRecentFiles(),
            cloudRepository.observeFiles(),
            cloudRepository.observeRefreshing(),
            cloudRepository.observeError(),
        ) { recentFiles, files, loading, err ->
            UiState(
                recentFiles = recentFiles,
                files = files,
                isLoading = loading,
                errorMessage = err,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState(isLoading = true),
        )

    fun refresh() {
        viewModelScope.launch {
            cloudRepository.refresh()
        }
    }

    fun clearError() {
        cloudRepository.clearError()
    }
}
