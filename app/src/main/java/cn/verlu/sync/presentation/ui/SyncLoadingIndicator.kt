package cn.verlu.sync.presentation.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Material 3 Expressive [LoadingIndicator]，统一替代 [androidx.compose.material3.CircularProgressIndicator]。
 */
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
