package cn.verlu.doctor.data.herb

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
/**
 * 将 /stats 返回的 JSON 转成普通人能看懂的短句，只保留常见重要字段。
 */
fun formatHerbStatsForDisplay(obj: JsonObject): List<String> {
    fun firstLong(vararg keys: String): Long? {
        for (k in keys) {
            val el = obj[k] ?: continue
            if (el is JsonPrimitive) {
                el.content.toLongOrNull()?.let { return it }
                el.content.toDoubleOrNull()?.let { return it.toLong() }
            }
        }
        return null
    }

    fun firstInt(vararg keys: String): Int? {
        for (k in keys) {
            val el = obj[k] ?: continue
            if (el is JsonPrimitive) {
                el.content.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    val lines = mutableListOf<String>()

    firstLong("total", "total_articles", "articles", "count", "indexed_total")?.let {
        lines.add("全书共收录约 $it 篇")
    }
    firstLong("shennong", "shennong_count", "volume_shennong", "shennong_total")?.let {
        lines.add("《神农本草经》相关：约 $it 篇")
    }
    firstLong("other", "other_count", "volume_other", "other_total")?.let {
        lines.add("其他文献：约 $it 篇")
    }
    firstLong("total_bytes", "bytes_total", "size_bytes_total")?.let { bytes ->
        lines.add("正文数据合计约 ${formatBytesHuman(bytes)}")
    }
    val minS = firstInt("serial_min", "min_serial", "serial_start")
    val maxS = firstInt("serial_max", "max_serial", "serial_end")
    if (minS != null && maxS != null) {
        lines.add("条文序号范围：$minS～$maxS")
    }

    return lines.distinct().ifEmpty {
        listOf("暂无可用统计摘要")
    }
}

private fun formatBytesHuman(bytes: Long): String {
    if (bytes < 1024) return "$bytes 字节"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
