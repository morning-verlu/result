package cn.verlu.cloud.presentation.files

import androidx.compose.runtime.Composable

/** 文件选择结果（跨平台）。 */
data class FilePickResult(
    val name: String,
    val mimeType: String?,
    /** 原始文件大小（字节）。 */
    val sizeBytes: Long,
    /**
     * 分片读取器：
     * @param offset 文件内偏移（字节）
     * @param length 读取长度（字节）
     * @return 最多 length 字节；到 EOF 可返回更短数组
     */
    val readRange: suspend (offset: Long, length: Int) -> ByteArray,
)

/**
 * 返回一个"打开文件选择器"的 lambda。
 * 平台实现：JVM → JFileChooser，Android → ActivityResultContracts.GetContent。
 *
 * @param onResult 选择完成后在主线程回调；用户取消则传空列表。
 */
@Composable
expect fun rememberFilePicker(onResult: (List<FilePickResult>) -> Unit): () -> Unit

/** Desktop-only: 监听窗口文件拖拽；Android 上为空实现。 */
@Composable
expect fun DesktopFileDropEffect(
    onFilesDropped: (List<FilePickResult>) -> Unit,
    onDragStateChange: (Boolean) -> Unit = {},
)
