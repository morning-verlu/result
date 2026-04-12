package cn.verlu.sync.presentation.temperature.mvi

import cn.verlu.sync.domain.model.TemperatureLevel

object TemperatureContract {
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: List<TemperatureLevel> = emptyList(),
        val myUserId: String? = null,
        val error: String? = null
    )

    sealed interface Intent {
        data object Start : Intent
        data class Refresh(val isSilent: Boolean = false) : Intent
        data class SetMyUserId(val userId: String?) : Intent
    }
}
