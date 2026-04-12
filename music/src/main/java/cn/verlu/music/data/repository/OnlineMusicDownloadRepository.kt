package cn.verlu.music.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineMusicDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun enqueue(title: String, artist: String, url: String): Long {
        val uri = Uri.parse(url)
        require(uri.scheme == "http" || uri.scheme == "https") { "不支持的下载链接" }

        val request = DownloadManager.Request(uri)
            .setTitle(title)
            .setDescription(artist)
            .setMimeType("audio/mpeg")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MUSIC,
                buildFileName(title, artist)
            )

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    private fun buildFileName(title: String, artist: String): String {
        val base = "$title-$artist"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "track_${System.currentTimeMillis()}" }
        return "$base.mp3"
    }
}
