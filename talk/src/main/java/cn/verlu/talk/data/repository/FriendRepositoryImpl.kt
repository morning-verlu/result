package cn.verlu.talk.data.repository

import android.util.Log
import cn.verlu.talk.data.local.dao.FriendshipDao
import cn.verlu.talk.data.local.entity.toEntity
import cn.verlu.talk.data.local.entity.toDomain as entityToDomain
import cn.verlu.talk.data.remote.dto.FriendshipDto
import cn.verlu.talk.data.remote.dto.NewFriendshipDto
import cn.verlu.talk.data.remote.dto.ProfileDto
import cn.verlu.talk.data.remote.dto.toDomain
import cn.verlu.talk.di.IoDispatcher
import cn.verlu.talk.domain.model.Friendship
import cn.verlu.talk.domain.model.FriendStatus
import cn.verlu.talk.domain.model.Profile
import cn.verlu.talk.util.parseTimestampToMs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.min

private const val TAG = "Talk/FriendRepo"

/** 双方互相发申请并都同意后，Supabase 可能有两条 accepted 记录；按 peer 只保留一条（优先有 room、再按 updated_at 新）。 */
private fun dedupeAcceptedByPeer(userId: String, accepted: List<FriendshipDto>): List<FriendshipDto> {
    if (accepted.size <= 1) return accepted
    return accepted
        .groupBy { dto -> if (dto.requesterId == userId) dto.addresseeId else dto.requesterId }
        .values
        .mapNotNull { rows ->
            rows.maxWithOrNull(
                compareByDescending<FriendshipDto> { it.roomId != null }
                    .thenByDescending { parseTimestampToMs(it.updatedAt) }
                    .thenByDescending { parseTimestampToMs(it.createdAt) },
            )
        }
}

class FriendRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val friendshipDao: FriendshipDao,
) : FriendRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val friendChannelName = "friendship_changes"
    private var activeChannel: RealtimeChannel? = null
    private var realtimeRetryJob: Job? = null
    private var keepRealtimeSubscription = false

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    private suspend fun fetchProfilesForIds(ids: List<String>): Map<String, Profile> {
        if (ids.isEmpty()) return emptyMap()
        return supabase.postgrest["profiles"].select {
            filter { isIn("id", ids) }
        }.decodeList<ProfileDto>().associate { it.id to it.toDomain() }
    }

    private fun FriendshipDto.toDomainWithProfiles(profileMap: Map<String, Profile>): Friendship =
        Friendship(
            id = id,
            requesterId = requesterId,
            addresseeId = addresseeId,
            status = when (status) {
                "accepted" -> FriendStatus.ACCEPTED
                "blocked" -> FriendStatus.BLOCKED
                else -> FriendStatus.PENDING
            },
            createdAtMs = parseTimestampToMs(createdAt),
            roomId = roomId,
            requesterProfile = profileMap[requesterId],
            addresseeProfile = profileMap[addresseeId],
        )

    // ─────────── Observe (Room → UI) ───────────

    override fun observeAcceptedFriends(): Flow<List<Friendship>> =
        friendshipDao.observeFriends().map { list -> list.map { it.entityToDomain() } }

    override fun observePendingRequests(userId: String): Flow<List<Friendship>> =
        friendshipDao.observePending(userId).map { list -> list.map { it.entityToDomain() } }

    // ─────────── Refresh (Network → Room) ───────────

    override suspend fun refreshFriends(): Unit = withContext(ioDispatcher) {
        val userId = currentUserId() ?: run {
            Log.d(TAG, "refreshFriends: not authenticated, skip")
            return@withContext
        }
        Log.d(TAG, "refreshFriends: userId=$userId")

        // fetch accepted
        val accepted = runCatching {
            supabase.postgrest["friendships"].select {
                filter {
                    eq("status", "accepted")
                    or {
                        eq("requester_id", userId)
                        eq("addressee_id", userId)
                    }
                }
            }.decodeList<FriendshipDto>()
        }.onFailure { Log.e(TAG, "refreshFriends: fetch accepted failed", it) }
            .getOrDefault(emptyList())

        val dedupedAccepted = dedupeAcceptedByPeer(userId, accepted)
        val droppedAcceptedIds = accepted.map { it.id }.toSet() - dedupedAccepted.map { it.id }.toSet()
        if (droppedAcceptedIds.isNotEmpty()) {
            Log.w(TAG, "refreshFriends: removing ${droppedAcceptedIds.size} duplicate accepted rows from local DB")
            friendshipDao.deleteByIds(droppedAcceptedIds.toList())
        }

        // fetch pending (where I'm the addressee)
        val pending = runCatching {
            supabase.postgrest["friendships"].select {
                filter {
                    eq("status", "pending")
                    eq("addressee_id", userId)
                }
            }.decodeList<FriendshipDto>()
        }.onFailure { Log.e(TAG, "refreshFriends: fetch pending failed", it) }
            .getOrDefault(emptyList())

        val allDtos = (dedupedAccepted + pending).distinctBy { it.id }
        val allIds = allDtos.flatMap { listOf(it.requesterId, it.addresseeId) }.distinct()
        val profileMap = runCatching { fetchProfilesForIds(allIds) }
            .onFailure { Log.e(TAG, "refreshFriends: fetch profiles failed", it) }
            .getOrDefault(emptyMap())

        val entities = allDtos.map { it.toDomainWithProfiles(profileMap).toEntity() }
        friendshipDao.upsertAll(entities)
        Log.d(TAG, "refreshFriends: upserted ${entities.size} friendships to Room")
    }

    // ─────────── Realtime ───────────

    private suspend fun subscribeRealtimeOnce(): Boolean {
        activeChannel?.let { old ->
            runCatching { supabase.realtime.removeChannel(old) }
                .onFailure { Log.w(TAG, "remove previous friendship channel failed", it) }
        }

        val channel = supabase.realtime.channel(friendChannelName)
        activeChannel = channel

        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "friendships"
        }.onEach {
            Log.d(TAG, "realtime: friendship INSERT, refreshing")
            repoScope.launch { runCatching { refreshFriends() }
                .onFailure { Log.e(TAG, "realtime friendship refresh failed", it) } }
        }.launchIn(repoScope)

        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "friendships"
        }.onEach {
            Log.d(TAG, "realtime: friendship UPDATE, refreshing")
            repoScope.launch { runCatching { refreshFriends() }
                .onFailure { Log.e(TAG, "realtime friendship refresh failed", it) } }
        }.launchIn(repoScope)

        channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "friendships"
        }.onEach {
            Log.d(TAG, "realtime: friendship DELETE, refreshing")
            repoScope.launch { runCatching { refreshFriends() }
                .onFailure { Log.e(TAG, "realtime friendship DELETE refresh failed", it) } }
        }.launchIn(repoScope)

        return runCatching { channel.subscribe() }
            .onFailure {
                Log.e(TAG, "friendship realtime subscribe failed", it)
                runCatching { supabase.realtime.removeChannel(channel) }
                activeChannel = null
            }
            .isSuccess
    }

    private fun startRealtimeRetryLoop() {
        realtimeRetryJob?.cancel()
        realtimeRetryJob = repoScope.launch {
            var attempt = 0
            while (isActive && keepRealtimeSubscription) {
                val ok = subscribeRealtimeOnce()
                if (ok) {
                    Log.d(TAG, "friendship realtime subscribed")
                    return@launch
                }
                attempt += 1
                val delayMs = 1_000L * (1 shl min(attempt, 5))
                Log.w(TAG, "friendship realtime retry in ${delayMs}ms (attempt=$attempt)")
                delay(delayMs)
            }
        }
    }

    override suspend fun subscribeToFriendshipChanges() {
        Log.d(TAG, "subscribeToFriendshipChanges: channel=$friendChannelName")
        keepRealtimeSubscription = true
        startRealtimeRetryLoop()
    }

    override suspend fun unsubscribeFromFriendshipChanges() {
        keepRealtimeSubscription = false
        realtimeRetryJob?.cancel()
        realtimeRetryJob = null
        runCatching {
            activeChannel?.let { supabase.realtime.removeChannel(it) }
        }.onFailure { Log.w(TAG, "unsubscribeFromFriendshipChanges failed", it) }
        activeChannel = null
    }

    // ─────────── Mutations ───────────

    override suspend fun sendFriendRequest(addresseeId: String) {
        withContext(ioDispatcher) {
            val userId = currentUserId() ?: error("未登录")
            Log.d(TAG, "sendFriendRequest: from=$userId to=$addresseeId")
            supabase.postgrest["friendships"].insert(
                NewFriendshipDto(requesterId = userId, addresseeId = addresseeId)
            )
        }
    }

    override suspend fun acceptFriendRequest(friendshipId: String) {
        withContext(ioDispatcher) {
            Log.d(TAG, "acceptFriendRequest: id=$friendshipId")
            supabase.postgrest["friendships"].update(
                mapOf("status" to "accepted", "updated_at" to Instant.now().toString())
            ) {
                filter { eq("id", friendshipId) }
            }
            // Room will update via realtime; also do a quick local update
            friendshipDao.markAccepted(friendshipId, roomId = null)
        }
    }

    override suspend fun rejectFriendRequest(friendshipId: String) {
        withContext(ioDispatcher) {
            Log.d(TAG, "rejectFriendRequest: id=$friendshipId")
            supabase.postgrest["friendships"].delete {
                filter { eq("id", friendshipId) }
            }
            friendshipDao.delete(friendshipId)
        }
    }

    override suspend fun searchUser(query: String): Profile? = withContext(ioDispatcher) {
        val q = query.trim()
        val uuidRegex = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            RegexOption.IGNORE_CASE
        )
        val results = when {
            uuidRegex.matches(q) ->
                supabase.postgrest["profiles"].select {
                    filter { eq("id", q) }
                    limit(1L)
                }.decodeList<ProfileDto>()
            q.contains("@") ->
                supabase.postgrest["profiles"].select {
                    filter { eq("email", q.lowercase()) }
                    limit(1L)
                }.decodeList<ProfileDto>()
            else -> return@withContext null
        }
        results.firstOrNull()?.toDomain()
    }
}
