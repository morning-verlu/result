package cn.verlu.cnchess.data.repository

import cn.verlu.cnchess.data.remote.dto.FriendshipDto
import cn.verlu.cnchess.data.remote.dto.ProfileDto
import cn.verlu.cnchess.data.remote.dto.toDomain
import cn.verlu.cnchess.di.IoDispatcher
import cn.verlu.cnchess.domain.model.Friendship
import cn.verlu.cnchess.domain.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FriendRepository {

    private val _friends = MutableStateFlow<List<Friendship>>(emptyList())
    override val friends: StateFlow<List<Friendship>> = _friends.asStateFlow()

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val friendChannelName = "cnchess_friendship_changes"

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    override suspend fun refreshFriends(): Unit = withContext(ioDispatcher) {
        val userId = currentUserId() ?: return@withContext
        val accepted = supabase.from("friendships").select {
            filter {
                eq("status", "accepted")
                or {
                    eq("requester_id", userId)
                    eq("addressee_id", userId)
                }
            }
        }.decodeList<FriendshipDto>()

        val peerIds = accepted.flatMap { listOf(it.requesterId, it.addresseeId) }.distinct()
        val profileMap = fetchProfiles(peerIds)
        _friends.value = accepted
            .map { dto ->
                Friendship(
                    id = dto.id,
                    requesterId = dto.requesterId,
                    addresseeId = dto.addresseeId,
                    roomId = dto.roomId,
                    requesterProfile = profileMap[dto.requesterId],
                    addresseeProfile = profileMap[dto.addresseeId],
                )
            }
            .sortedBy { it.peerProfile(userId)?.displayName.orEmpty() }
    }

    override suspend fun subscribeToFriendshipChanges() {
        val channel = supabase.realtime.channel(friendChannelName)
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "friendships"
        }.onEach {
            repoScope.launch { runCatching { refreshFriends() } }
        }.launchIn(repoScope)
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "friendships"
        }.onEach {
            repoScope.launch { runCatching { refreshFriends() } }
        }.launchIn(repoScope)
        channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "friendships"
        }.onEach {
            repoScope.launch { runCatching { refreshFriends() } }
        }.launchIn(repoScope)
        channel.subscribe()
    }

    override suspend fun unsubscribeFromFriendshipChanges() {
        runCatching {
            supabase.realtime.removeChannel(supabase.realtime.channel(friendChannelName))
        }
    }

    private suspend fun fetchProfiles(ids: List<String>): Map<String, Profile> {
        if (ids.isEmpty()) return emptyMap()
        return supabase.from("profiles").select {
            filter {
                isIn("id", ids)
            }
        }.decodeList<ProfileDto>().associate { it.id to it.toDomain() }
    }
}
