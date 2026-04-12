package cn.verlu.cloud.presentation.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileInputStream

internal object ShareIntentParser {
    fun parse(context: Context, intent: Intent?): List<FilePickResult> {
        if (intent == null) return emptyList()
        val action = intent.action ?: return emptyList()
        val uris = when (action) {
            Intent.ACTION_SEND -> {
                val single = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (single != null) listOf(single) else emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: emptyList()
            }
            else -> emptyList()
        }
        if (uris.isEmpty()) return emptyList()
        val cr = context.contentResolver
        return uris.mapNotNull { uri ->
            runCatching {
                val mimeType = cr.getType(uri)
                val name = cr.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    if (idx >= 0) cursor.getString(idx) else null
                } ?: uri.lastPathSegment ?: "shared_file"
                val size = cr.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    cursor.moveToFirst()
                    if (idx >= 0) cursor.getLong(idx) else -1L
                } ?: -1L
                FilePickResult(
                    name = name,
                    mimeType = mimeType,
                    sizeBytes = size.coerceAtLeast(0L),
                    readRange = { offset, length ->
                        runCatching {
                            cr.openFileDescriptor(uri, "r")?.use { pfd ->
                                FileInputStream(pfd.fileDescriptor).use { fis ->
                                    fis.channel.position(offset.coerceAtLeast(0L))
                                    val target = ByteArray(length.coerceAtLeast(0))
                                    var readTotal = 0
                                    while (readTotal < target.size) {
                                        val r = fis.read(target, readTotal, target.size - readTotal)
                                        if (r <= 0) break
                                        readTotal += r
                                    }
                                    if (readTotal <= 0) ByteArray(0) else target.copyOf(readTotal)
                                }
                            }
                        }.getOrElse {
                            cr.openInputStream(uri)?.use { input ->
                                var toSkip = offset.coerceAtLeast(0L)
                                while (toSkip > 0L) {
                                    val skipped = input.skip(toSkip)
                                    if (skipped <= 0L) break
                                    toSkip -= skipped
                                }
                                val target = ByteArray(length.coerceAtLeast(0))
                                var readTotal = 0
                                while (readTotal < target.size) {
                                    val r = input.read(target, readTotal, target.size - readTotal)
                                    if (r <= 0) break
                                    readTotal += r
                                }
                                if (readTotal <= 0) ByteArray(0) else target.copyOf(readTotal)
                            } ?: ByteArray(0)
                        } ?: ByteArray(0)
                    }
                )
            }.getOrNull()
        }
    }
}
