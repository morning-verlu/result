package cn.verlu.doctor.presentation.herb.vm

import cn.verlu.doctor.data.herb.HerbNoAccessTokenYet

/** JWT 尚未就绪时不展示错误条；其余展示原因或默认文案。 */
internal fun Throwable.herbUserVisibleError(): String? =
    when (this) {
        is HerbNoAccessTokenYet -> null
        else -> message?.takeIf { it.isNotBlank() } ?: "加载失败"
    }
