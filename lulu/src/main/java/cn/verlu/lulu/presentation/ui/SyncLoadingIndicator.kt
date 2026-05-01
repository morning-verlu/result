package cn.verlu.lulu.presentation.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Material 3 Expressive [LoadingIndicator]，统一替代环形进度条。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LoadingIndicator(
        modifier = modifier,
        color = color,
        polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    PullToRefreshDefaults.LoadingIndicator(
        modifier = modifier,
        state = state,
        isRefreshing = isRefreshing,
        containerColor = containerColor,
        color = color,
    )
}
