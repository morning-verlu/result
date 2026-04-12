package cn.verlu.sync.data.stats

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * 「使用情况访问权限」没有运行时弹窗，只能进系统页授权。
 * [Settings.ACTION_USAGE_ACCESS_SETTINGS] 仍是官方入口；Android 10+ 可带 `package:` 尽量定位到本应用（各厂商表现不一致）。
 */
object UsageAccessSettingsNavigator {
    fun open(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            intent.data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
    }
}
