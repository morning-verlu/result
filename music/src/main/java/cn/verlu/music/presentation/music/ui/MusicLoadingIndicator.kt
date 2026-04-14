package cn.verlu.music.presentation.music.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Material 3 Expressive [LoadingIndicator]（替代传统环形进度条）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    LoadingIndicator(
        modifier = modifier,
        color = color,
        polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
    )
}
