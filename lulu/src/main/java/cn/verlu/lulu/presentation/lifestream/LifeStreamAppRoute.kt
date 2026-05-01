package cn.verlu.lulu.presentation.lifestream

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.verlu.lulu.feature.lifestream.presentation.navigation.MemoryNavApp

@Composable
fun LifeStreamAppRoute(
    modifier: Modifier = Modifier,
) {
    MemoryNavApp(
        modifier = modifier,
        embeddedInLulu = true,
    )
}
