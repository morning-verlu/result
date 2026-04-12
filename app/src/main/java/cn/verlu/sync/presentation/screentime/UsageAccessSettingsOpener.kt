package cn.verlu.sync.presentation.screentime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * 「使用情况访问权限」没有运行时对话框，只能进系统页由用户打开。
 * [Settings.ACTION_USAGE_ACCESS_SETTINGS] 仍是官方入口；Android 10+ 可带 `package:` 便于定位本应用（各厂商实现不一致，失败时回退无 data 的 Intent）。
 */
object UsageAccessSettingsOpener {
    fun open(context: Context) {
        val withPkg = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = "package:${context.packageName}".toUri()
        }
        runCatching { context.startActivity(withPkg) }.onFailure {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
