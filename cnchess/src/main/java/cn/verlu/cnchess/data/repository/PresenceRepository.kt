package cn.verlu.cnchess.data.repository

interface PresenceRepository {
    suspend fun setForeground(isForeground: Boolean)
    suspend fun heartbeat()
    suspend fun isUserOnline(userId: String, withinSeconds: Long = 45): Boolean
}
