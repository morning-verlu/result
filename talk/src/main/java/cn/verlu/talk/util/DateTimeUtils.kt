package cn.verlu.talk.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun parseTimestampToMs(ts: String?): Long {
    if (ts.isNullOrBlank()) return System.currentTimeMillis()
    return try {
        val normalized = ts.trim().replace(" ", "T")
            .let { if (it.length == 19) "${it}+00:00" else it }
        java.time.OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.Instant.parse(ts).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}

fun formatConversationTime(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val now = Calendar.getInstance()
    return when {
        cal.get(Calendar.YEAR) != now.get(Calendar.YEAR) ->
            SimpleDateFormat("yy/M/d", Locale.CHINA).format(cal.time)
        now.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) == 1 -> "昨天"
        cal.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("M月d日", Locale.CHINA).format(cal.time)
        else ->
            SimpleDateFormat("HH:mm", Locale.CHINA).format(cal.time)
    }
}

fun formatMessageTimestamp(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val now = Calendar.getInstance()
    return when {
        cal.get(Calendar.YEAR) != now.get(Calendar.YEAR) ->
            SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(cal.time)
        cal.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(cal.time)
        else ->
            SimpleDateFormat("HH:mm", Locale.CHINA).format(cal.time)
    }
}

/** 相邻两条消息超过 5 分钟时插入时间分割线 */
fun shouldShowTimeSeparator(current: Long, previous: Long?): Boolean {
    if (previous == null) return true
    return current - previous > 5 * 60 * 1000L
}
