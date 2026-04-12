package cn.verlu.sync.presentation.weather.mvi

import cn.verlu.sync.domain.model.WeatherSnapshot

object WeatherContract {
    data class State(
        val items: List<WeatherSnapshot> = emptyList(),
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val myLocation: WeatherSnapshot? = null,
        val needsLocationPermission: Boolean = false
    )

    sealed interface Intent {
        data object Start : Intent
        data class Refresh(val isSilent: Boolean = false) : Intent
        data class LocationPermissionResult(val granted: Boolean) : Intent
        data object UpdateLocation : Intent
        data object DismissError : Intent
    }
}
