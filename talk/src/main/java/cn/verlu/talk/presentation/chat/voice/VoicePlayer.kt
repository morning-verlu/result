package cn.verlu.talk.presentation.chat.voice

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "Talk/VoicePlayer"

/**
 * 进程内单例语音播放器：保证同一时刻只播放一条语音。
 * 通过 [playingUrl] flow 让所有气泡 UI 共享当前播放状态。
 */
object VoicePlayer {
    private val _playingUrl = MutableStateFlow<String?>(null)
    val playingUrl: StateFlow<String?> = _playingUrl.asStateFlow()

    private var player: MediaPlayer? = null
    private var currentUrl: String? = null

    private fun clearCurrent(expected: MediaPlayer? = null) {
        val target = expected ?: player
        if (expected != null && target !== player) return
        runCatching { target?.reset() }
        runCatching { target?.release() }
        if (target === player) {
            player = null
            currentUrl = null
            _playingUrl.value = null
        }
    }

    fun toggle(url: String) {
        if (currentUrl == url && player?.isPlaying == true) {
            stop()
        } else {
            play(url)
        }
    }

    fun play(url: String) {
        stop()
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(url)
                setOnCompletionListener {
                    clearCurrent(it)
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    clearCurrent(mp)
                    true
                }
                prepareAsync()
                setOnPreparedListener { prepared ->
                    prepared.start()
                    _playingUrl.value = url
                }
            }
            player = mp
            currentUrl = url
        }.onFailure {
            Log.e(TAG, "play failed", it)
            stop()
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        clearCurrent()
    }
}

/** 解析语音消息内容 `<durationMs>|<url>`；旧版兼容时退化为整段当 URL。 */
fun parseVoiceContent(content: String): Pair<Long, String>? {
    val idx = content.indexOf('|')
    if (idx <= 0) return null
    val ms = content.substring(0, idx).toLongOrNull() ?: return null
    val url = content.substring(idx + 1).takeIf { it.isNotBlank() } ?: return null
    return ms to url
}

fun formatVoiceDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000).toInt().coerceAtLeast(1)
    return "${totalSec}\""
}
