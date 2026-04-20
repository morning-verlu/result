package cn.verlu.talk.presentation.chat.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

private const val TAG = "Talk/VoiceRecorder"

/**
 * 一次性的语音录制器，输出 m4a (AAC LC, 单声道, 32kbps)。
 * 文件落到 `cacheDir/voices/`，发送成功后由调用方删除。
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L
    private var pausedAt: Long = 0L
    private var totalPausedMs: Long = 0L

    val isRecording: Boolean get() = recorder != null
    val isPaused: Boolean get() = pausedAt > 0L

    fun start(): Boolean {
        if (isRecording) return false
        return runCatching {
            val dir = File(context.cacheDir, "voices").apply { if (!exists()) mkdirs() }
            val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioChannels(1)
            mr.setAudioSamplingRate(44_100)
            mr.setAudioEncodingBitRate(32_000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            startedAt = System.currentTimeMillis()
            pausedAt = 0L
            totalPausedMs = 0L
            true
        }.getOrElse {
            Log.e(TAG, "start() failed", it)
            cleanup(deleteFile = true)
            false
        }
    }

    /** 停止录音；返回 (audioBytes, durationMs)。失败或太短返回 null。 */
    fun stop(minDurationMs: Long = 600L): Pair<ByteArray, Long>? {
        val mr = recorder ?: return null
        val now = System.currentTimeMillis()
        val pausedCarry = if (pausedAt > 0L) (now - pausedAt) else 0L
        val durationMs = now - startedAt - totalPausedMs - pausedCarry
        return runCatching {
            mr.stop()
            mr.release()
            recorder = null

            val f = outputFile ?: return@runCatching null
            if (durationMs < minDurationMs) {
                f.delete()
                outputFile = null
                null
            } else {
                val bytes = f.readBytes()
                f.delete()
                outputFile = null
                bytes to durationMs
            }
        }.getOrElse {
            Log.e(TAG, "stop() failed", it)
            cleanup(deleteFile = true)
            null
        }
    }

    fun pause(): Boolean {
        val mr = recorder ?: return false
        if (pausedAt > 0L) return true
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mr.pause()
                pausedAt = System.currentTimeMillis()
                true
            } else {
                false
            }
        }.getOrElse {
            Log.e(TAG, "pause() failed", it)
            false
        }
    }

    fun resume(): Boolean {
        val mr = recorder ?: return false
        if (pausedAt <= 0L) return true
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mr.resume()
                totalPausedMs += (System.currentTimeMillis() - pausedAt)
                pausedAt = 0L
                true
            } else {
                false
            }
        }.getOrElse {
            Log.e(TAG, "resume() failed", it)
            false
        }
    }

    fun cancel() {
        cleanup(deleteFile = true)
    }

    private fun cleanup(deleteFile: Boolean) {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        if (deleteFile) {
            outputFile?.delete()
        }
        outputFile = null
        startedAt = 0L
        pausedAt = 0L
        totalPausedMs = 0L
    }
}
