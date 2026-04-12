package cn.verlu.music.data.repository

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadTaskItem(
    val id: Long,
    val title: String,
    val artist: String?,
    val status: Int,
    val soFar: Long,
    val total: Long,
    val localUri: String?
)

@Singleton
class DownloadManagerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun queryAll(): List<DownloadTaskItem> {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        val result = ArrayList<DownloadTaskItem>()
        manager.query(query)?.use { c ->
            while (c.moveToNext()) {
                result += c.toItem()
            }
        }
        return result.sortedByDescending { it.id }
    }

    fun remove(id: Long): Int {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.remove(id)
    }

    private fun Cursor.toItem(): DownloadTaskItem {
        val id = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
        val title = getString(getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "未知标题"
        val artist = getString(getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION))
        val status = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val soFar = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val localUri = getString(getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        return DownloadTaskItem(id, title, artist, status, soFar, total, localUri)
    }
}
