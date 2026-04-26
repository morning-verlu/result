package cn.verlu.memory.presentation.ui

import cn.verlu.memory.core.log.MemoryLog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MemoryLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    reason: String = "unspecified",
) {
    DisposableEffect(reason) {
        MemoryLog.i("MemoryLoadingIndicator", "[loading] start reason=$reason")
        onDispose {
            MemoryLog.i("MemoryLoadingIndicator", "[loading] end reason=$reason")
        }
    }
    LoadingIndicator(
        modifier = modifier,
        color = color,
        polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
    )
}
