package cn.verlu.talk.presentation.auth

import android.content.Context
import android.content.Intent
import android.net.Uri

/** 与 Sync 应用 `applicationId` 一致，用于显式拉起，避免出现「用哪個應用開啟」選擇器。 */
private const val SYNC_APP_PACKAGE = "cn.verlu.sync"
private const val RETURN_PKG_TALK = "cn.verlu.talk"

/**
 * 使用已登录的 Sync 完成 SSO 授权。
 *
 * 注意：不要依赖 [PackageManager.resolveActivity] 预判——在 Android 11+ 与显式
 * [Intent.setPackage] 组合下，部分机型会误返回 null，但 [Context.startActivity] 仍可成功。
 */
fun tryLaunchSyncSsoAuthorize(context: Context, sessionId: String): Boolean {
    val uri = Uri.parse("verlusync://authorize_sso").buildUpon()
        .appendQueryParameter("sessionId", sessionId)
        .appendQueryParameter("returnPkg", RETURN_PKG_TALK)
        .build()
    val preferSync = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        setPackage(SYNC_APP_PACKAGE)
    }
    try {
        context.startActivity(preferSync)
        return true
    } catch (_: Exception) {
        // 回退：与旧版一致，不显式包名（极少数环境显式拉起失败时仍可用）
    }
    val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(fallback)
        true
    } catch (_: Exception) {
        false
    }
}
