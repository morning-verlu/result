package cn.verlu.sync.domain.model

data class BatteryLevel(
    val userId: String,
    val deviceFriendlyName: String,
    val deviceModel: String,
    val batteryPercent: Int,
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
}
