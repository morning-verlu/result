package cn.verlu.sync.presentation.weather.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.sync.domain.repository.WeatherRepository
import cn.verlu.sync.presentation.weather.mvi.WeatherContract
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherContract.State())
    val state: StateFlow<WeatherContract.State> = _state.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null
    private var syncJob: kotlinx.coroutines.Job? = null

    fun dispatch(intent: WeatherContract.Intent) {
        when (intent) {
            WeatherContract.Intent.Start -> start()
            is WeatherContract.Intent.Refresh -> refresh(isSilent = intent.isSilent)
            is WeatherContract.Intent.LocationPermissionResult -> onLocationPermission(intent.granted)
            WeatherContract.Intent.UpdateLocation -> updateLocation()
            WeatherContract.Intent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun start() {
        if (syncJob?.isActive == true) return
        
        syncJob = viewModelScope.launch {
            runCatching { weatherRepository.startSync() }
                .onSuccess { 
                    _state.update { s -> s.copy(error = null) }
                    // 启动成功后，根据是否有缓存位置来决定刷新策略
                    refresh(isSilent = true)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "天气同步启动失败"
                        )
                    }
                }
        }

        viewModelScope.launch {
            val myUserId = weatherRepository.getCurrentUserId()
            weatherRepository.observeAll().collectLatest { items ->
                _state.update { s ->
                    s.copy(
                        isLoading = false,
                        items = items,
                        myLocation = items.find { it.userId == myUserId }
                    )
                }
            }
        }
    }

    private fun onLocationPermission(granted: Boolean) {
        _state.update { it.copy(needsLocationPermission = !granted) }
        if (granted) {
            refresh(isSilent = true)
        }
    }

    private fun updateLocation() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            runCatching { weatherRepository.refreshWithCurrentLocation() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "定位更新失败") }
                }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    private fun refresh(isSilent: Boolean) {
        val currentLoc = _state.value.myLocation
        if (currentLoc == null) {
            // 如果没位置，走常规获取流程
            updateLocation()
            return
        }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isRefreshing = !isSilent,
                    error = null
                )
            }
            try {
                runCatching { 
                    weatherRepository.refreshWithLocation(
                        currentLoc.latitude, 
                        currentLoc.longitude, 
                        currentLoc.cityLabel
                    ) 
                }
                    .onFailure { e ->
                        _state.update {
                            it.copy(error = e.message ?: "刷新失败")
                        }
                    }
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }
}
