package cn.verlu.cloud.presentation.auth

import kotlinx.coroutines.flow.MutableSharedFlow

object AuthDeepLinkBus {
    val links = MutableSharedFlow<String>(extraBufferCapacity = 8)
}
