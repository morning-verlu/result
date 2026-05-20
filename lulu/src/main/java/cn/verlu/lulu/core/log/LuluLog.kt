package cn.verlu.lulu.core.log

import android.util.Log
import cn.verlu.lulu.BuildConfig

object LuluLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    fun shortId(value: String?, visible: Int = 8): String =
        value
            ?.takeIf { it.isNotBlank() }
            ?.let { "${it.take(visible.coerceAtLeast(1))}..." }
            ?: "null"
}
