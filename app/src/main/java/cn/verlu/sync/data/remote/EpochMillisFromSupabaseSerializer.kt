package cn.verlu.sync.data.remote

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 读：PostgREST 对 `timestamptz` 常返回 ISO 字符串，对 `bigint` 毫秒返回数字，两者都解码为 epoch 毫秒。
 * 写：按 JSON 数字写出，匹配 `bigint`/`int8` 的 `updated_at`；若列是 `timestamptz` 请改用数据库 bigint 或单独 DTO。
 */
object EpochMillisFromSupabaseSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor(
        "EpochMillisFromSupabase",
        PrimitiveKind.LONG
    )

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("EpochMillisFromSupabaseSerializer requires Json format")
        val element = jsonDecoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive
            ?: error("Expected JSON primitive for updated_at")
        primitive.longOrNull?.let { return it }
        if (primitive.isString) {
            return parseSupabaseTimestamp(primitive.content)
        }
        error("Cannot parse updated_at: $element")
    }

    override fun serialize(encoder: Encoder, value: Long) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("EpochMillisFromSupabaseSerializer requires Json format")
        // 写入：bigint / int8 列必须用 JSON 数字；timestamptz 请改表结构或换用专用 DTO
        jsonEncoder.encodeJsonElement(JsonPrimitive(value))
    }

    private fun parseSupabaseTimestamp(s: String): Long = try {
        Instant.parse(s).toEpochMilli()
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: Exception) {
            LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
    }
}
