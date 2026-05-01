package cn.verlu.lulu.feature.cnchess.presentation.auth.vm

import kotlinx.coroutines.flow.MutableStateFlow

object AuthEventManager {
    val showPasswordResetDialog = MutableStateFlow(false)
}
