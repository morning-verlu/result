package cn.verlu.memory.presentation.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.verlu.memory.presentation.lifestream.ui.LifeStreamScreen

@Composable
fun AuthenticatedHome(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LifeStreamScreen(
        onSignOut = onSignOut,
        modifier = modifier,
    )
}
