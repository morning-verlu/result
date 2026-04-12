package cn.verlu.cloud.presentation.files

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberFilePicker(onResult: (List<FilePickResult>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isEmpty()) {
            onResult(emptyList())
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            try {
                val cr = context.contentResolver
                val picks = uris.mapNotNull { uri ->
                    val mimeType = cr.getType(uri)
                    val name = cr.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        if (idx >= 0) cursor.getString(idx) else null
                    } ?: uri.lastPathSegment ?: "upload"
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
                                // 优先使用 PFD + FileChannel.position 做随机读，避免每次从头 skip。
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
                                // 个别 provider 不支持随机读时，回退到 InputStream + skip。
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
                        },
                    )
                }
                withContext(Dispatchers.Main) {
                    onResult(picks)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    return { launcher.launch("*/*") }
}

@Composable
actual fun DesktopFileDropEffect(
    onFilesDropped: (List<FilePickResult>) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
) = Unit
