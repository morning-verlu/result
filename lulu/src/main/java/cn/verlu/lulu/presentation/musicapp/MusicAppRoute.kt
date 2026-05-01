package cn.verlu.lulu.presentation.musicapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.verlu.lulu.feature.music.presentation.navigation.MusicNavApp

@Composable
fun MusicAppRoute(
    modifier: Modifier = Modifier,
    onExit: () -> Unit,
) {
    MusicNavApp(
        modifier = modifier,
        onExit = onExit,
        embeddedInLulu = true,
    )
}
