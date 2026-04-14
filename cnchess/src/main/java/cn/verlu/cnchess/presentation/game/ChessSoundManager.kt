package cn.verlu.cnchess.presentation.game

import android.content.Context
import android.media.MediaPlayer

/**
 * Plays check sound effect from app resources.
 * Expected file name: cnchess_check (e.g. cnchess_check.mp3 in res/raw).
 */
class ChessSoundManager {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun playCheck() {
        val context = appContext ?: return
        val resId = context.resources.getIdentifier("cnchess_check", "raw", context.packageName)
        if (resId == 0) return
        runCatching {
            MediaPlayer.create(context, resId)?.apply {
                setOnCompletionListener { player -> player.release() }
                setOnErrorListener { player, _, _ ->
                    player.release()
                    true
                }
                start()
            }
        }
    }

    fun release() {
        appContext = null
    }
}
