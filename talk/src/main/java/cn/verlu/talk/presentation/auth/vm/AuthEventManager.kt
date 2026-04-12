package cn.verlu.talk.presentation.auth.vm

import kotlinx.coroutines.flow.MutableStateFlow

object AuthEventManager {
    val showPasswordResetDialog = MutableStateFlow(false)
}
