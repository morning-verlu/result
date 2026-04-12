package cn.verlu.doctor.presentation.auth

import android.content.Context
import android.content.Intent
import android.net.Uri

private const val SYNC_APP_PACKAGE = "cn.verlu.sync"
private const val RETURN_PKG_DOCTOR = "cn.verlu.doctor"

fun tryLaunchSyncSsoAuthorize(context: Context, sessionId: String): Boolean {
    val uri = Uri.parse("verlusync://authorize_sso").buildUpon()
        .appendQueryParameter("sessionId", sessionId)
        .appendQueryParameter("returnPkg", RETURN_PKG_DOCTOR)
        .build()
    val preferSync = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        setPackage(SYNC_APP_PACKAGE)
    }
    try {
        context.startActivity(preferSync)
        return true
    } catch (_: Exception) {
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
