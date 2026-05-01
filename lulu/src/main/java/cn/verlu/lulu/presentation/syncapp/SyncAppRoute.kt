package cn.verlu.lulu.presentation.syncapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.verlu.lulu.feature.sync.presentation.navigation.SyncNavApp

@Composable
fun SyncAppRoute(
    modifier: Modifier = Modifier,
) {
    SyncNavApp(
        modifier = modifier,
        embeddedInLulu = true,
    )
}
