package cn.verlu.lulu.presentation.talk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.verlu.lulu.feature.talk.presentation.navigation.TalkNavApp

@Composable
fun TalkRoute(
    modifier: Modifier = Modifier,
) {
    TalkNavApp(
        modifier = modifier,
        embeddedInLulu = true,
    )
}
