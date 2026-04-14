package cn.verlu.cnchess.util

import java.time.Instant

fun parseTimestampToMs(ts: String?): Long? {
    if (ts.isNullOrBlank()) return null
    return runCatching { Instant.parse(ts).toEpochMilli() }.getOrNull()
}
