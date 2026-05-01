package cn.verlu.lulu.presentation.doctor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.verlu.lulu.feature.doctor.presentation.navigation.DoctorNavApp

@Composable
fun DoctorRoute(
    modifier: Modifier = Modifier,
) {
    DoctorNavApp(
        modifier = modifier,
        embeddedInLulu = true,
    )
}
