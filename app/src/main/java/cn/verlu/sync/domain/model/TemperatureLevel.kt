package cn.verlu.sync.domain.model

data class TemperatureLevel(
    val userId: String,
    val deviceFriendlyName: String,
    val deviceModel: String,
    val temperature: Int,
    val updatedAt: Long
) {
    val displayLabel: String
        get() {
            val name = deviceFriendlyName.trim()
            if (name.isNotEmpty()) return name
            val m = deviceModel.trim()
            if (m.isNotEmpty()) return m
            val id = userId
            return if (id.length > 14) "${id.take(12)}…" else id
        }

    val displayTemperature: String
        get() = "${temperature / 10.0}°C"
}
