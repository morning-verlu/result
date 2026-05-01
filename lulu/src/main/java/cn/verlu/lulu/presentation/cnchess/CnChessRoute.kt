package cn.verlu.lulu.presentation.cnchess

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.verlu.lulu.feature.cnchess.presentation.navigation.CnChessNavApp

@Composable
fun CnChessRoute(
    modifier: Modifier = Modifier,
) {
    CnChessNavApp(
        modifier = modifier,
        embeddedInLulu = true,
    )
}
