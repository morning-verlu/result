package cn.verlu.sync.presentation.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 下拉刷新顶部指示器：使用 Material3 Expressive 的 [PullToRefreshDefaults.LoadingIndicator]，
 * 替代默认的环形 [PullToRefreshDefaults.Indicator]。
 */
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
