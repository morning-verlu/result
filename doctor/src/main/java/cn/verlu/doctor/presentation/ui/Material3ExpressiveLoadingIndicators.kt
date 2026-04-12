package cn.verlu.doctor.presentation.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Material3 Expressive 的 [LoadingIndicator]（多边形动画），用于替代 [androidx.compose.material3.CircularProgressIndicator]。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Material3ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    LoadingIndicator(
        modifier = modifier,
        color = LoadingIndicatorDefaults.indicatorColor,
        polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
    )
}
