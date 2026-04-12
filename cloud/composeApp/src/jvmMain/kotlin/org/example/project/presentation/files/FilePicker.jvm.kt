package cn.verlu.cloud.presentation.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.UIManager
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import javax.swing.SwingUtilities

@Composable
actual fun rememberFilePicker(onResult: (List<FilePickResult>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch(Dispatchers.IO) {
            var results: List<FilePickResult> = emptyList()
            SwingUtilities.invokeAndWait {
                // 使用系统 LAF 的 JFileChooser，视觉上比 AWT FileDialog 更现代。
                runCatching {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                }
                val chooser = JFileChooser().apply {
                    dialogTitle = "选择要上传的文件"
                    isMultiSelectionEnabled = true
                }
                if (chooser.showOpenDialog(findVisibleWindow()) == JFileChooser.APPROVE_OPTION) {
                    val pickedFiles = chooser.selectedFiles?.toList()?.takeIf { it.isNotEmpty() }
                        ?: chooser.selectedFile?.let { listOf(it) }
                        ?: emptyList()
                    results = pickedFiles.mapNotNull { file ->
                        runCatching {
                            val mime = Files.probeContentType(file.toPath())
                            FilePickResult(
                                name = file.name,
                                mimeType = mime,
                                sizeBytes = file.length(),
                                readRange = { offset, length ->
                                    RandomAccessFile(file, "r").use { raf ->
                                        raf.seek(offset.coerceAtLeast(0L))
                                        val buf = ByteArray(length.coerceAtLeast(0))
                                        val read = raf.read(buf)
                                        if (read <= 0) ByteArray(0) else buf.copyOf(read)
                                    }
                                },
                            )
                        }.getOrNull()
                    }
                }
            }
            withContext(Dispatchers.Main) { onResult(results) }
        }
    }
}

@Composable
actual fun DesktopFileDropEffect(
    onFilesDropped: (List<FilePickResult>) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestCallback = rememberUpdatedState(onFilesDropped)
    val latestDragStateChange = rememberUpdatedState(onDragStateChange)

    DisposableEffect(Unit) {
        val window = findVisibleWindow()
        if (window == null) return@DisposableEffect onDispose { }

        val targetComponents = buildList<Component> {
            add(window)
            collectAllChildren(window).forEach { add(it) }
        }
        val originalDropTargets = targetComponents.associateWith { it.dropTarget }

        val dropTarget = DropTarget(null, object : DropTargetAdapter() {
            override fun dragEnter(event: DropTargetDragEvent) {
                if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    latestDragStateChange.value(true)
                    event.acceptDrag(DnDConstants.ACTION_COPY)
                } else {
                    event.rejectDrag()
                }
            }

            override fun dragExit(event: java.awt.dnd.DropTargetEvent?) {
                latestDragStateChange.value(false)
            }

            override fun drop(event: DropTargetDropEvent) {
                latestDragStateChange.value(false)
                if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    event.rejectDrop()
                    return
                }
                event.acceptDrop(DnDConstants.ACTION_COPY)
                val files = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                }.getOrElse {
                    event.dropComplete(false)
                    return
                }
                event.dropComplete(true)

                scope.launch(Dispatchers.IO) {
                    val picks = files
                        .filter { it.isFile }
                        .mapNotNull { file ->
                            runCatching {
                                val mime = Files.probeContentType(file.toPath())
                                FilePickResult(
                                    name = file.name,
                                    mimeType = mime,
                                    sizeBytes = file.length(),
                                    readRange = { offset, length ->
                                        RandomAccessFile(file, "r").use { raf ->
                                            raf.seek(offset.coerceAtLeast(0L))
                                            val buf = ByteArray(length.coerceAtLeast(0))
                                            val read = raf.read(buf)
                                            if (read <= 0) ByteArray(0) else buf.copyOf(read)
                                        }
                                    },
                                )
                            }.getOrNull()
                        }
                    if (picks.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            latestCallback.value(picks)
                        }
                    }
                }
            }
        })

        targetComponents.forEach { it.dropTarget = dropTarget }
        onDispose {
            latestDragStateChange.value(false)
            originalDropTargets.forEach { (component, original) ->
                component.dropTarget = original
            }
        }
    }
}

private fun findVisibleWindow(): Window? {
    val active = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    if (active != null && active.isShowing) return active
    return Window.getWindows().firstOrNull { it.isShowing }
}

private fun collectAllChildren(root: Component): List<Component> {
    if (root !is Container) return emptyList()
    return root.components.flatMap { child ->
        listOf(child) + collectAllChildren(child)
    }
}
