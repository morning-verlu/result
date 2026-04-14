package cn.verlu.sync.presentation.weather.ui

import android.Manifest
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.sync.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.sync.presentation.navigation.LocalSnackbarHostState
import cn.verlu.sync.presentation.weather.mvi.WeatherContract
import cn.verlu.sync.presentation.weather.vm.WeatherViewModel

@Composable
fun WeatherRoute(
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel = hiltViewModel(),
    authSessionViewModel: AuthSessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by authSessionViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val myUserId = authState.user?.id
    val snackbarHostState = LocalSnackbarHostState.current

    fun computeHasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    var hasLocationPermission by remember { mutableStateOf(computeHasLocationPermission()) }
 
    LaunchedEffect(state.error, hasLocationPermission) {
        state.error?.let { err ->
            val isPermissionRelated = err.contains("权限") || err.contains("定位")
            if (!(!hasLocationPermission && isPermissionRelated)) {
                snackbarHostState.showSnackbar(err)
            }
            viewModel.dispatch(WeatherContract.Intent.DismissError)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val granted = fine || coarse
        hasLocationPermission = granted
        viewModel.dispatch(WeatherContract.Intent.LocationPermissionResult(granted))
    }

    LaunchedEffect(Unit) {
        viewModel.dispatch(WeatherContract.Intent.Start)
        hasLocationPermission = computeHasLocationPermission()
        if (hasLocationPermission) {
            viewModel.dispatch(WeatherContract.Intent.LocationPermissionResult(true))
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) {
        WeatherScreen(
            state = state,
            myUserId = myUserId,
            hasLocationPermission = hasLocationPermission,
            modifier = Modifier.fillMaxSize(),
            onRefresh = {
                if (computeHasLocationPermission()) {
                    hasLocationPermission = true
                    viewModel.dispatch(WeatherContract.Intent.Refresh())
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onRequestLocationPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onUpdateLocation = {
                viewModel.dispatch(WeatherContract.Intent.UpdateLocation)
            }
        )
    }
}
