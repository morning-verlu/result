package cn.verlu.cloud.platform

import android.content.Intent
import android.net.Uri
import cn.verlu.cloud.data.local.AndroidPlatformContext

actual fun openExternalUrl(url: String): Result<Unit> = runCatching {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AndroidPlatformContext.require().startActivity(intent)
}
