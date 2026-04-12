package cn.verlu.cloud.presentation.files

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Android 系统“分享至 Cloud”入口桥接：
 * MainActivity 解析外部 URI 后投递到这里，Explorer 页面消费并加入上传队列。
 */
object IncomingShareBus {
    private val pending = ArrayDeque<List<FilePickResult>>()
    val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    fun offer(picks: List<FilePickResult>) {
        if (picks.isEmpty()) return
        synchronized(pending) { pending.addLast(picks) }
        changed.tryEmit(Unit)
    }

    fun pollAll(): List<FilePickResult> {
        val result = mutableListOf<FilePickResult>()
        synchronized(pending) {
            while (pending.isNotEmpty()) {
                result += pending.removeFirst()
            }
        }
        return result
    }
}
