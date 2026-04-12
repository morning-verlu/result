package cn.verlu.sync.data.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceModelLabel @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** 系统设置里的「设备名称」（蓝牙/本机显示），可能为空。 */
    fun getFriendlyName(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.trim()
            .orEmpty()

    /** 厂商 + 型号，用于区分硬件。 */
    fun get(): String {
        val parts = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return parts.joinToString(" ").ifBlank { "Android" }
    }
}
