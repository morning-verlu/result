package cn.verlu.memory.core.log

import android.util.Log

object MemoryLog {
    private val debugEnabled: Boolean by lazy {
        runCatching {
            val clazz = Class.forName("cn.verlu.memory.BuildConfig")
            clazz.getField("DEBUG").getBoolean(null)
        }.getOrDefault(false)
    }

    fun d(tag: String, message: String) {
        if (debugEnabled) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
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
}
