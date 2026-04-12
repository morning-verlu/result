package cn.verlu.music.domain.lyrics

/**
 * 解析 LRC 时间轴歌词，例如 `[00:26.00]为你熬了多少个不眠的夜`。
 * 时间格式：`[mm:ss.xx]`，点号后为百分秒（两位）或毫秒（三位）。
 */
object LrcParser {

    data class Line(val timeMs: Long, val text: String)

    private val lineRegex = Regex("""^\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?\]\s*(.*)$""")

    fun parse(raw: String?): List<Line> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<Line>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val m = lineRegex.matchEntire(trimmed) ?: continue
            val mm = m.groupValues[1].toInt()
            val ss = m.groupValues[2].toInt()
            val frac = m.groupValues[3]
            val text = m.groupValues[4].trim()
            if (text.isEmpty()) continue
            val timeMs = toTimeMs(mm, ss, frac)
            out.add(Line(timeMs, text))
        }
        out.sortBy { it.timeMs }
        return out
    }

    private fun toTimeMs(mm: Int, ss: Int, frac: String): Long {
        val base = mm * 60_000L + ss * 1000L
        if (frac.isEmpty()) return base
        val extra = when (frac.length) {
            2 -> frac.toLong() * 10L
            3 -> frac.toLong().coerceIn(0, 999)
            1 -> frac.toLong() * 100L
            else -> frac.take(2).toLongOrNull()?.times(10) ?: 0L
        }
        return base + extra
    }
}
