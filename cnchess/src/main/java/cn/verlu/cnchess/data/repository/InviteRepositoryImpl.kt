package cn.verlu.cnchess.data.repository

import cn.verlu.cnchess.data.remote.dto.InviteDto
import cn.verlu.cnchess.data.remote.dto.ProfileDto
import cn.verlu.cnchess.data.remote.dto.toDomain
import cn.verlu.cnchess.di.IoDispatcher
import cn.verlu.cnchess.domain.model.ChessInvite
import cn.verlu.cnchess.domain.model.InviteStatus
import cn.verlu.cnchess.domain.model.Profile
import cn.verlu.cnchess.util.parseTimestampToMs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
private data class NewInviteDto(
    val from_user_id: String,
    val to_user_id: String,
    val status: String = "pending",
    val expires_at: String,
)

@Serializable
private data class NewGameDto(
    val id: String,
    val red_user_id: String,
    val black_user_id: String,
    val status: String = "active",
)

@Singleton
class InviteRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val presenceRepository: PresenceRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : InviteRepository {

    private val _incomingPendingInvites = MutableStateFlow<List<ChessInvite>>(emptyList())
    override val incomingPendingInvites: StateFlow<List<ChessInvite>> = _incomingPendingInvites.asStateFlow()

    private val _outgoingPendingInvites = MutableStateFlow<List<ChessInvite>>(emptyList())
    override val outgoingPendingInvites: StateFlow<List<ChessInvite>> = _outgoingPendingInvites.asStateFlow()

    private val _gameLaunchEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val gameLaunchEvents: Flow<String> = _gameLaunchEvents.asSharedFlow()

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inviteChannelName = "cnchess_invite_changes"
    private val emittedGameIds = linkedSetOf<String>()

    /** 仅对「刚被接受」的邀请自动进房；避免冷启动把历史已结束对局再导航一遍。 */
    private val autoOpenInviteMaxAgeMs = 120_000L

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    override suspend fun refreshInvites(): Unit = withContext(ioDispatcher) {
        val userId = currentUserId() ?: return@withContext
        expireStaleInvites(userId)

        val rows = supabase.from("chess_invites").select {
            filter {
                or {
                    eq("from_user_id", userId)
                    eq("to_user_id", userId)
                }
            }
        }.decodeList<InviteDto>()

        val userIds = rows.flatMap { listOf(it.fromUserId, it.toUserId) }.distinct()
        val profileMap = fetchProfiles(userIds)
        val mapped = rows.map { it.toDomain(profileMap) }

        _incomingPendingInvites.value = mapped
            .filter { it.toUserId == userId && it.status == InviteStatus.PENDING }
            .sortedByDescending { it.expiresAtMs ?: 0L }
        _outgoingPendingInvites.value = mapped
            .filter { it.fromUserId == userId && it.status == InviteStatus.PENDING }
            .sortedByDescending { it.expiresAtMs ?: 0L }

        val nowMs = System.currentTimeMillis()
        mapped.filter { it.status == InviteStatus.ACCEPTED && it.gameId != null }
            .forEach { invite ->
                val gameId = invite.gameId ?: return@forEach
                val updatedMs = invite.updatedAtMs ?: return@forEach
                if (nowMs - updatedMs > autoOpenInviteMaxAgeMs) return@forEach
                if (emittedGameIds.add(gameId)) {
                    _gameLaunchEvents.tryEmit(gameId)
                }
            }
    }

    override suspend fun subscribeInviteChanges() {
        val channel = supabase.realtime.channel(inviteChannelName)
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "chess_invites"
        }.onEach {
            repoScope.launch { runCatching { refreshInvites() } }
        }.launchIn(repoScope)
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "chess_invites"
        }.onEach {
            repoScope.launch { runCatching { refreshInvites() } }
        }.launchIn(repoScope)
        channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "chess_invites"
        }.onEach {
            repoScope.launch { runCatching { refreshInvites() } }
        }.launchIn(repoScope)
        channel.subscribe()
    }

    override suspend fun unsubscribeInviteChanges() {
        runCatching {
            supabase.realtime.removeChannel(supabase.realtime.channel(inviteChannelName))
        }
    }

    override suspend fun sendInvite(toUserId: String): Unit = withContext(ioDispatcher) {
        val fromUserId = currentUserId() ?: error("未登录")
        if (fromUserId == toUserId) error("不能邀请自己")
        val online = presenceRepository.isUserOnline(toUserId)
        if (!online) error("对方当前不在线，暂时无法邀请")

        val expiresAt = Instant.ofEpochMilli(System.currentTimeMillis() + 60_000).toString()
        supabase.from("chess_invites").insert(
            NewInviteDto(
                from_user_id = fromUserId,
                to_user_id = toUserId,
                expires_at = expiresAt,
            ),
        )
        refreshInvites()
    }

    override suspend fun rejectInvite(inviteId: String): Unit = withContext(ioDispatcher) {
        val uid = currentUserId() ?: error("未登录")
        supabase.from("chess_invites").update(
            mapOf(
                "status" to "rejected",
                "updated_at" to Instant.now().toString(),
            ),
        ) {
            filter {
                eq("id", inviteId)
                eq("to_user_id", uid)
                eq("status", "pending")
            }
        }
        refreshInvites()
    }

    override suspend fun cancelInvite(inviteId: String): Unit = withContext(ioDispatcher) {
        val uid = currentUserId() ?: error("未登录")
        supabase.from("chess_invites").update(
            mapOf(
                "status" to "canceled",
                "updated_at" to Instant.now().toString(),
            ),
        ) {
            filter {
                eq("id", inviteId)
                eq("from_user_id", uid)
                eq("status", "pending")
            }
        }
        refreshInvites()
    }

    override suspend fun acceptInvite(inviteId: String): String = withContext(ioDispatcher) {
        val uid = currentUserId() ?: error("未登录")
        val invite = supabase.from("chess_invites").select {
            filter {
                eq("id", inviteId)
                eq("to_user_id", uid)
                eq("status", "pending")
            }
        }.decodeSingle<InviteDto>()

        val nowMs = System.currentTimeMillis()
        val expiresAtMs = parseTimestampToMs(invite.expiresAt)
        if (expiresAtMs != null && expiresAtMs <= nowMs) {
            rejectInvite(inviteId)
            error("邀请已过期")
        }

        val gameId = UUID.randomUUID().toString()
        val redFirst = nowMs % 2L == 0L
        val redUserId = if (redFirst) invite.fromUserId else invite.toUserId
        val blackUserId = if (redFirst) invite.toUserId else invite.fromUserId
        supabase.from("chess_games").insert(
            NewGameDto(
                id = gameId,
                red_user_id = redUserId,
                black_user_id = blackUserId,
            ),
        )
        supabase.from("chess_invites").update(
            mapOf(
                "status" to "accepted",
                "game_id" to gameId,
                "updated_at" to Instant.now().toString(),
            ),
        ) {
            filter {
                eq("id", inviteId)
                eq("status", "pending")
            }
        }

        refreshInvites()
        _gameLaunchEvents.tryEmit(gameId)
        gameId
    }

    private suspend fun expireStaleInvites(userId: String) {
        supabase.from("chess_invites").update(
            mapOf(
                "status" to "expired",
                "updated_at" to Instant.now().toString(),
            ),
        ) {
            filter {
                eq("status", "pending")
                lt("expires_at", Instant.now().toString())
                or {
                    eq("from_user_id", userId)
                    eq("to_user_id", userId)
                }
            }
        }
    }

    private suspend fun fetchProfiles(userIds: List<String>): Map<String, Profile> {
        if (userIds.isEmpty()) return emptyMap()
        return supabase.from("profiles").select {
            filter {
                isIn("id", userIds)
            }
        }.decodeList<ProfileDto>().associate { it.id to it.toDomain() }
    }

    private fun InviteDto.toDomain(profileMap: Map<String, Profile>): ChessInvite {
        val statusValue = when (status.lowercase()) {
            "accepted" -> InviteStatus.ACCEPTED
            "rejected" -> InviteStatus.REJECTED
            "expired" -> InviteStatus.EXPIRED
            "canceled" -> InviteStatus.CANCELED
            else -> InviteStatus.PENDING
        }
        return ChessInvite(
            id = id,
            fromUserId = fromUserId,
            toUserId = toUserId,
            status = statusValue,
            gameId = gameId,
            expiresAtMs = parseTimestampToMs(expiresAt),
            updatedAtMs = parseTimestampToMs(updatedAt),
            fromProfile = profileMap[fromUserId],
            toProfile = profileMap[toUserId],
        )
    }
}
