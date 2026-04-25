package cn.verlu.memory.presentation.auth.vm

import kotlinx.coroutines.flow.MutableStateFlow

object AuthEventManager {
    val showPasswordResetDialog = MutableStateFlow(false)
}
