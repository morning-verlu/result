package cn.verlu.cnchess.data.repository

import cn.verlu.cnchess.data.remote.dto.PresenceDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.Serializable

@Serializable
private data class PresenceUpsertDto(
    val user_id: String,
    val is_foreground: Boolean,
    val last_seen_at: String,
    val device_id: String,
)

class PresenceRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : PresenceRepository {

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    override suspend fun setForeground(isForeground: Boolean) {
        upsertPresence(isForeground = isForeground)
    }

    override suspend fun heartbeat() {
        upsertPresence(isForeground = true)
    }

    override suspend fun isUserOnline(userId: String, withinSeconds: Long): Boolean {
        val nowMs = System.currentTimeMillis()
        val rows = supabase.from("cnchess_presence").select(
            columns = Columns.list("user_id,last_seen_at,is_foreground"),
        ) {
            filter {
                eq("user_id", userId)
            }
        }.decodeList<PresenceDto>()
        val row = rows.firstOrNull() ?: return false
        if (!row.isForeground) return false
        val seenMs = runCatching { Instant.parse(row.lastSeenAt).toEpochMilli() }.getOrNull() ?: return false
        return nowMs - seenMs <= withinSeconds * 1000
    }

    private suspend fun upsertPresence(isForeground: Boolean) {
        val uid = currentUserId() ?: return
        val payload = PresenceUpsertDto(
            user_id = uid,
            is_foreground = isForeground,
            last_seen_at = Instant.now().toString(),
            device_id = "android",
        )
        supabase.from("cnchess_presence").upsert(payload) {
            onConflict = "user_id"
        }
    }
}
