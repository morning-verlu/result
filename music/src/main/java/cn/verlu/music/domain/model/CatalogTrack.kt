package cn.verlu.music.domain.model

import android.net.Uri

data class CatalogTrack(
    val id: Long,
    val rid: String,
    val song: String,
    val singer: String,
    val pic: String? = null
) {
    /**
     * 转为 [LocalAudio]：id 取负，避免与本地 MediaStore 正 id 在收藏等表里冲突。
     */
    fun toLocalAudio(url: String, lrc: String?): LocalAudio {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: Uri.EMPTY
        return LocalAudio(
            id = -id,
            uri = parsed,
            title = song,
            artist = singer,
            album = "在线音乐",
            durationMs = 0L,
            albumId = 0L,
            coverUrl = pic,
            lrc = lrc
        )
    }
}
