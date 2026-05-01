package cn.verlu.lulu.presentation.cloud

import cn.verlu.lulu.domain.cloud.CloudFile

object CloudContract {
    data class UiState(
        val recentFiles: List<CloudFile> = emptyList(),
        val files: List<CloudFile> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )
}
