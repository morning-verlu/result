package cn.verlu.sync.presentation.home.mvi

import cn.verlu.sync.domain.model.BatteryLevel

object HomeContract {
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: List<BatteryLevel> = emptyList(),
        val myUserId: String? = null,
        val error: String? = null
    )

    sealed interface Intent {
        data object Start : Intent
        data class Refresh(val isSilent: Boolean = false) : Intent
        data class SetMyUserId(val userId: String?) : Intent
    }
}
