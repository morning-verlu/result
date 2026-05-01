package cn.verlu.lulu.data.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import cn.verlu.lulu.domain.sync.BatteryStatus
import cn.verlu.lulu.domain.sync.BatteryStatusReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidBatteryStatusReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BatteryStatusReader {
    override fun readBatteryStatus(): BatteryStatus {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            null
        }

        return BatteryStatus(
            percent = percent,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }
}
