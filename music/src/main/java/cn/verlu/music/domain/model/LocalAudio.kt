package cn.verlu.music.domain.model

import android.net.Uri

data class LocalAudio(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: Long,
    /** 在线歌曲封面 URL；本地扫描一般为 null */
    val coverUrl: String? = null,
    /** 在线曲库 LRC 原文；本地扫描一般为 null */
    val lrc: String? = null
)
