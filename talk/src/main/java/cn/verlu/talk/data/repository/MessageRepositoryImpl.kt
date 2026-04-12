package cn.verlu.talk.data.repository

import android.util.Log
import cn.verlu.talk.data.local.dao.ConversationDao
import cn.verlu.talk.data.local.dao.MessageDao
import cn.verlu.talk.data.local.entity.toEntity
import cn.verlu.talk.data.local.entity.toDomain as entityToDomain
import cn.verlu.talk.data.remote.dto.FriendshipDto
import cn.verlu.talk.data.remote.dto.MessageDto
import cn.verlu.talk.data.remote.dto.NewMessageDto
import cn.verlu.talk.data.remote.dto.ProfileDto
import cn.verlu.talk.data.remote.dto.toDomain
import cn.verlu.talk.di.IoDispatcher
import cn.verlu.talk.domain.model.Conversation
import cn.verlu.talk.domain.model.Message
import cn.verlu.talk.domain.model.MessageType
import cn.verlu.talk.domain.model.Profile
import cn.verlu.talk.util.parseTimestampToMs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

private const val TAG = "Talk/MessageRepo"

private fun dedupeAcceptedFriendshipsByPeer(userId: String, rows: List<FriendshipDto>): List<FriendshipDto> {
    if (rows.size <= 1) return rows
    return rows
        .groupBy { f -> if (f.requesterId == userId) f.addresseeId else f.requesterId }
        .values
        .mapNotNull { group ->
            group.maxWithOrNull(
                compareByDescending<FriendshipDto> { it.roomId != null }
                    .thenByDescending { parseTimestampToMs(it.updatedAt) }
                    .thenByDescending { parseTimestampToMs(it.createdAt) },
            )
        }
}

class MessageRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
) : MessageRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var convChannelName: String? = null
    private var convOnUpdate: (() -> Unit)? = null

    private suspend fun currentUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("未登录")

    private suspend fun upsertMessageFromDto(roomId: String, dto: MessageDto) {
        val profile = runCatching {
            supabase.postgrest["profiles"].select {
                filter { eq("id", dto.senderId) }
                limit(1L)
            }.decodeList<ProfileDto>().firstOrNull()?.toDomain()
        }.getOrNull()
        val msg = dto.toDomain(profile)
        messageDao.upsert(msg.toEntity())
        conversationDao.updateLastMessage(
            roomId = roomId,
            content = if (msg.isDeleted) null else msg.content,
            atMs = msg.createdAtMs,
            type = msg.type.name.lowercase(),
            deleted = msg.isDeleted,
        )
    }

    private fun MessageDto.toDomain(senderProfile: Profile? = null): Message = Message(
        id = id,
        roomId = roomId,
        senderId = senderId,
        content = content,
        type = when (type) {
            "image" -> MessageType.IMAGE
            "location" -> MessageType.LOCATION
            else -> MessageType.TEXT
        },
        createdAtMs = parseTimestampToMs(createdAt),
        isDeleted = deletedAt != null,
        senderProfile = senderProfile,
    )

    // ─────────── Observe (Room → UI) ───────────

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { list -> list.map { it.entityToDomain() } }

    override fun observeMessages(roomId: String): Flow<List<Message>> =
        messageDao.observe(roomId).map { list -> list.map { it.entityToDomain() } }

    // ─────────── Refresh (Network → Room) ───────────

    override suspend fun refreshConversations() = withContext(ioDispatcher) {
        val userId = currentUserId()
        Log.d(TAG, "refreshConversations: userId=$userId")

        val friendships = runCatching {
            supabase.postgrest["friendships"].select {
                filter {
                    eq("status", "accepted")
                    or {
                        eq("requester_id", userId)
                        eq("addressee_id", userId)
                    }
                }
            }.decodeList<FriendshipDto>()
        }.onFailure { Log.e(TAG, "refreshConversations: fetch friendships failed", it) }
            .getOrDefault(emptyList())

        val merged = dedupeAcceptedFriendshipsByPeer(userId, friendships)
        if (merged.size < friendships.size) {
            Log.w(TAG, "refreshConversations: deduped friendships ${friendships.size} -> ${merged.size}")
        }

        val withRoom = merged.filter { it.roomId != null }
        Log.d(TAG, "refreshConversations: friendships=${merged.size} withRoom=${withRoom.size}")
        if (withRoom.isEmpty()) {
            conversationDao.deleteAll()
            return@withContext
        }

        val peerIdByRoom: Map<String, String> = withRoom.associate { f ->
            f.roomId!! to if (f.requesterId == userId) f.addresseeId else f.requesterId
        }

        val peerIds = peerIdByRoom.values.distinct()
        val profileMap: Map<String, Profile> = runCatching {
            supabase.postgrest["profiles"].select {
                filter { isIn("id", peerIds) }
            }.decodeList<ProfileDto>().associate { it.id to it.toDomain() }
        }.onFailure { Log.e(TAG, "refreshConversations: fetch profiles failed", it) }
            .getOrDefault(emptyMap())

        val roomIds = withRoom.mapNotNull { it.roomId }
        val lastMessages: Map<String, Message?> = roomIds.map { roomId ->
            async {
                val msg = runCatching {
                    supabase.postgrest["messages"].select {
                        filter { eq("room_id", roomId) }
                        order("created_at", Order.DESCENDING)
                        limit(1L)
                    }.decodeList<MessageDto>().firstOrNull()
                }.onFailure { Log.e(TAG, "refreshConversations: last msg for $roomId failed", it) }
                    .getOrNull()
                roomId to msg?.toDomain()
            }
        }.awaitAll().toMap()

        val conversations = withRoom.mapNotNull { f ->
            val roomId = f.roomId ?: return@mapNotNull null
            val peerId = peerIdByRoom[roomId] ?: return@mapNotNull null
            val peer = profileMap[peerId] ?: run {
                Log.w(TAG, "refreshConversations: no profile for peerId=$peerId")
                return@mapNotNull null
            }
            Conversation(roomId = roomId, peer = peer, lastMessage = lastMessages[roomId])
        }

        conversationDao.replaceAll(conversations.map { it.toEntity() })
        Log.d(TAG, "refreshConversations: replaced ${conversations.size} conversations in Room")
    }

    override suspend fun refreshMessages(roomId: String): Unit = withContext(ioDispatcher) {
        Log.d(TAG, "refreshMessages: roomId=$roomId")
        val messages = runCatching {
            supabase.postgrest["messages"].select {
                filter { eq("room_id", roomId) }
                order("created_at", Order.ASCENDING)
                limit(200L)
            }.decodeList<MessageDto>()
        }.onFailure { Log.e(TAG, "refreshMessages failed roomId=$roomId", it) }
            .getOrDefault(emptyList())

        val senderIds = messages.map { it.senderId }.distinct()
        val profileMap: Map<String, Profile> = if (senderIds.isNotEmpty()) {
            runCatching {
                supabase.postgrest["profiles"].select {
                    filter { isIn("id", senderIds) }
                }.decodeList<ProfileDto>().associate { it.id to it.toDomain() }
            }.onFailure { Log.e(TAG, "refreshMessages: fetch profiles failed", it) }
                .getOrDefault(emptyMap())
        } else emptyMap()

        val domainMessages = messages.map { it.toDomain(profileMap[it.senderId]) }
        messageDao.upsertAll(domainMessages.map { it.toEntity() })
        Log.d(TAG, "refreshMessages: upserted ${domainMessages.size} messages to Room")
    }

    // ─────────── Realtime ───────────

    override suspend fun subscribeToRoomMessages(roomId: String, onError: (Throwable) -> Unit) {
        val channelName = "room_messages_$roomId"
        Log.d(TAG, "subscribeToRoomMessages: subscribing to channel=$channelName")
        runCatching {
            val channel = supabase.realtime.channel(channelName)
            channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "messages"
            }.onEach { action ->
                runCatching {
                    val dto = action.decodeRecord<MessageDto>()
                    if (dto.roomId != roomId) return@runCatching
                    Log.d(TAG, "realtime: new message id=${dto.id} room=$roomId")
                    upsertMessageFromDto(roomId, dto)
                }.onFailure { Log.e(TAG, "realtime message processing failed", it) }
            }.launchIn(repoScope)
            channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "messages"
            }.onEach { action ->
                runCatching {
                    val dto = action.decodeRecord<MessageDto>()
                    if (dto.roomId != roomId) return@runCatching
                    Log.d(TAG, "realtime: message updated id=${dto.id} room=$roomId deleted=${dto.deletedAt != null}")
                    upsertMessageFromDto(roomId, dto)
                }.onFailure { Log.e(TAG, "realtime message update failed", it) }
            }.launchIn(repoScope)
            channel.subscribe()
        }.onFailure {
            Log.e(TAG, "subscribeToRoomMessages failed", it)
            onError(it)
        }
    }

    override suspend fun unsubscribeFromRoom(roomId: String) {
        val channelName = "room_messages_$roomId"
        runCatching {
            supabase.realtime.removeChannel(supabase.realtime.channel(channelName))
        }.onFailure { Log.w(TAG, "unsubscribeFromRoom failed", it) }
    }

    override suspend fun subscribeToConversationUpdates(onUpdate: () -> Unit) {
        val channelName = "conv_list_friendships"
        convChannelName = channelName
        convOnUpdate = onUpdate
        Log.d(TAG, "subscribeToConversationUpdates: channel=$channelName")
        runCatching {
            val channel = supabase.realtime.channel(channelName)
            channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "friendships"
            }.onEach {
                Log.d(TAG, "conv realtime: friendships updated, refreshing conversations")
                repoScope.launch {
                    runCatching { refreshConversations() }
                        .onFailure { Log.e(TAG, "conv realtime: refreshConversations failed", it) }
                    onUpdate()
                }
            }.launchIn(repoScope)
            channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "friendships"
            }.onEach {
                Log.d(TAG, "conv realtime: new friendship, refreshing conversations")
                repoScope.launch {
                    runCatching { refreshConversations() }
                        .onFailure { Log.e(TAG, "conv realtime: refreshConversations failed", it) }
                    onUpdate()
                }
            }.launchIn(repoScope)
            channel.subscribe()
        }.onFailure { Log.e(TAG, "subscribeToConversationUpdates failed", it) }
    }

    override suspend fun unsubscribeFromConversationUpdates() {
        convChannelName?.let { name ->
            runCatching {
                supabase.realtime.removeChannel(supabase.realtime.channel(name))
            }.onFailure { Log.w(TAG, "unsubscribeFromConversationUpdates failed", it) }
        }
    }

    override suspend fun findDirectRoomIdForPeer(peerUserId: String): String? = withContext(ioDispatcher) {
        val userId = currentUserId()
        Log.d(TAG, "findDirectRoomIdForPeer: me=$userId peer=$peerUserId")
        // Try Room cache first
        val fromCache = conversationDao.observeAll().map { list ->
            list.firstOrNull { it.peerUserId == peerUserId }?.roomId
        }
        // Quick one-shot from Room
        val friendship = runCatching {
            supabase.postgrest["friendships"].select {
                filter {
                    eq("status", "accepted")
                    or {
                        and {
                            eq("requester_id", userId)
                            eq("addressee_id", peerUserId)
                        }
                        and {
                            eq("requester_id", peerUserId)
                            eq("addressee_id", userId)
                        }
                    }
                }
                limit(1L)
            }.decodeList<FriendshipDto>().firstOrNull()
        }.onFailure { Log.e(TAG, "findDirectRoomIdForPeer failed", it) }.getOrNull()
        val roomId = friendship?.roomId
        Log.d(TAG, "findDirectRoomIdForPeer: roomId=$roomId")
        roomId
    }

    override suspend fun sendMessage(roomId: String, content: String, type: String) {
        withContext(ioDispatcher) {
            val userId = currentUserId()
            Log.d(TAG, "sendMessage: roomId=$roomId senderId=$userId content=${content.take(30)}")
            runCatching {
                supabase.postgrest["messages"].insert(
                    NewMessageDto(roomId = roomId, senderId = userId, content = content, type = type)
                )
            }.onSuccess {
                Log.d(TAG, "sendMessage: success")
            }.onFailure {
                Log.e(TAG, "sendMessage: FAILED", it)
                throw it
            }
        }
    }

    override suspend fun softDeleteMessage(messageId: String) {
        withContext(ioDispatcher) {
            Log.d(TAG, "softDeleteMessage: id=$messageId")
            supabase.postgrest["messages"].update(
                mapOf("deleted_at" to Instant.now().toString())
            ) {
                filter { eq("id", messageId) }
            }
            messageDao.markDeleted(messageId)
        }
    }

    override suspend fun markAllRead(roomId: String) {
        withContext(ioDispatcher) {
            val userId = currentUserId()
            runCatching {
                val allMsgIds = supabase.postgrest["messages"].select {
                    filter { eq("room_id", roomId) }
                }.decodeList<MessageDto>().map { it.id }

                if (allMsgIds.isNotEmpty()) {
                    val reads = allMsgIds.map { mapOf("message_id" to it, "user_id" to userId) }
                    supabase.postgrest["message_reads"].upsert(reads)
                }
            }.onFailure { Log.w(TAG, "markAllRead failed roomId=$roomId", it) }
        }
    }
}
