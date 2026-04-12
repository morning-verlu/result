package cn.verlu.cloud.data.local

import android.content.Context

object AndroidPlatformContext {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun require(): Context = checkNotNull(appContext) {
        "AndroidPlatformContext is not initialized"
    }
}
