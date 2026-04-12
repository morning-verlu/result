package cn.verlu.cloud.data.friends

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── 内部 DTO（与 Talk 共享同一张 Supabase 表，结构完全对齐）────────────────

@Serializable
private data class FriendshipDto(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: String = "pending",
    @SerialName("room_id") val roomId: String? = null,
)

@Serializable
private data class ProfileDto(
    val id: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
private data class NewMessageDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    val type: String = "text",
)

// ─── 接口 ─────────────────────────────────────────────────────────────────────

interface CloudFriendRepository {
    /**
     * 获取当前登录用户的所有已接受好友，包含对应的 roomId（聊天室）。
     * 结果已按显示名排序。
     */
    suspend fun getAcceptedFriends(): Result<List<CloudFriendItem>>

    /**
     * 向指定聊天室发送一条文本消息（内容为分享链接卡片文本）。
     */
    suspend fun sendMessageToRoom(roomId: String, content: String): Result<Unit>
}

// ─── Supabase 实现 ────────────────────────────────────────────────────────────

class SupabaseCloudFriendRepository(private val supabase: SupabaseClient) : CloudFriendRepository {

    private fun currentUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("未登录")

    override suspend fun getAcceptedFriends(): Result<List<CloudFriendItem>> = runCatching {
        val userId = currentUserId()

        // 1. 拉取 accepted 好友关系
        val friendships = supabase.postgrest["friendships"].select {
            filter {
                eq("status", "accepted")
                or {
                    eq("requester_id", userId)
                    eq("addressee_id", userId)
                }
            }
        }.decodeList<FriendshipDto>()

        if (friendships.isEmpty()) return@runCatching emptyList()

        // 2. 找出 peer 的 userId 列表（排除自己）
        val peerIds = friendships.map { f ->
            if (f.requesterId == userId) f.addresseeId else f.requesterId
        }.distinct()

        // 3. 批量拉取 peer profiles
        val profileMap: Map<String, ProfileDto> = supabase.postgrest["profiles"].select {
            filter { isIn("id", peerIds) }
        }.decodeList<ProfileDto>().associateBy { it.id }

        // 4. 拼装结果（优先有 roomId 的记录；同一 peer 可能有多条记录则保留 roomId 非空那条）
        friendships
            .groupBy { f -> if (f.requesterId == userId) f.addresseeId else f.requesterId }
            .mapNotNull { (peerId, group) ->
                val best = group.maxWithOrNull(
                    compareByDescending<FriendshipDto> { it.roomId != null }
                )
                val profile = profileMap[peerId] ?: return@mapNotNull null
                CloudFriendItem(
                    userId = peerId,
                    displayName = profile.displayName
                        ?: profile.email?.substringBefore("@")
                        ?: "未知用户",
                    email = profile.email,
                    avatarUrl = profile.avatarUrl,
                    roomId = best?.roomId,
                )
            }
            .sortedBy { it.displayName }
    }

    override suspend fun sendMessageToRoom(roomId: String, content: String): Result<Unit> = runCatching {
        val userId = currentUserId()
        supabase.postgrest["messages"].insert(
            NewMessageDto(roomId = roomId, senderId = userId, content = content)
        )
    }
}
