package cn.verlu.lulu.data.chat

import cn.verlu.lulu.data.local.dao.ChatDao
import cn.verlu.lulu.data.local.entity.ChatConversationEntity
import cn.verlu.lulu.data.local.entity.ChatMessageEntity
import cn.verlu.lulu.data.local.entity.toDomain
import cn.verlu.lulu.data.local.entity.toStorageJson
import cn.verlu.lulu.di.IoDispatcher
import cn.verlu.lulu.domain.chat.ChatConversation
import cn.verlu.lulu.domain.chat.ChatMemoryReference
import cn.verlu.lulu.domain.chat.ChatMessage
import cn.verlu.lulu.domain.chat.ChatMessageRole
import cn.verlu.lulu.domain.chat.ChatRepository
import cn.verlu.lulu.domain.memory.Memory
import cn.verlu.lulu.domain.memory.MemoryRepository
import cn.verlu.lulu.domain.memory.MemoryType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.utils.io.InternalAPI
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
class MemoryAwareChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val memoryRepository: MemoryRepository,
    private val supabase: SupabaseClient,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ChatRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observeConversations(): Flow<List<ChatConversation>> =
        chatDao.observeConversations().map { rows -> rows.map { it.toDomain() } }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        chatDao.observeMessages(conversationId).map { rows ->
            rows.map { it.toDomain(json) }
        }

    override suspend fun startConversation(): String = withContext(dispatcher) {
        val now = Instant.now().toEpochMilli()
        val id = UUID.randomUUID().toString()
        chatDao.upsertConversation(
            ChatConversationEntity(
                id = id,
                title = "新的对话",
                lastMessagePreview = "可以问我和记忆有关的事",
                createdAt = now,
                updatedAt = now,
                messageCount = 0,
                memoryContextCount = 0,
            )
        )
        id
    }

    override suspend fun sendMessage(
        conversationId: String?,
        content: String,
    ): String = withContext(dispatcher) {
        val text = content.trim()
        require(text.isNotBlank()) { "先写点想聊的内容" }

        val now = Instant.now().toEpochMilli()
        val id = conversationId ?: UUID.randomUUID().toString()
        val current = chatDao.getConversation(id)
        val title = current?.title?.takeUnless { it == "新的对话" } ?: text.toConversationTitle()

        val userMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = id,
            role = ChatMessageRole.User.name,
            content = text,
            createdAt = now,
            referencedMemoriesJson = emptyList<ChatMemoryReference>().toStorageJson(json),
        )

        val memories = memoryRepository.observeEntries().first()
        val references = memories
            .rankFor(text)
            .take(MAX_CONTEXT_MEMORIES)
            .map { it.toReference() }
        val recentMessages = current
            ?.let { chatDao.getRecentMessages(id, RECENT_HISTORY_LIMIT).asReversed() }
            ?: emptyList()
        val reply = generateReply(
            query = text,
            references = references,
            totalMemories = memories.size,
            recentMessages = recentMessages,
        )
        val assistantMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = id,
            role = ChatMessageRole.Assistant.name,
            content = reply.content,
            createdAt = now + 1,
            referencedMemoriesJson = references.toStorageJson(json),
        )

        chatDao.appendMessageAndUpdateConversation(
            message = userMessage,
            conversation = ChatConversationEntity(
                id = id,
                title = title,
                lastMessagePreview = text.take(PREVIEW_LIMIT),
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
                messageCount = chatDao.countMessages(id) + 1,
                memoryContextCount = references.size,
            ),
        )
        chatDao.appendMessageAndUpdateConversation(
            message = assistantMessage,
            conversation = ChatConversationEntity(
                id = id,
                title = title,
                lastMessagePreview = reply.content.take(PREVIEW_LIMIT),
                createdAt = current?.createdAt ?: now,
                updatedAt = now + 1,
                messageCount = chatDao.countMessages(id) + 1,
                memoryContextCount = references.size,
            ),
        )
        id
    }

    override suspend fun saveAssistantMessageAsMemory(messageId: String) = withContext(dispatcher) {
        val message = chatDao.getMessage(messageId) ?: error("消息不存在")
        require(message.role == ChatMessageRole.Assistant.name) { "只能保存 Lulu 的回复" }
        memoryRepository.createEntry(
            title = message.content.toConversationTitle(),
            content = message.content,
            type = MemoryType.Idea,
            tags = listOf("Lulu Chat", "聊天整理"),
            mood = "",
            scene = "聊天",
        )
    }

    private suspend fun generateReply(
        query: String,
        references: List<ChatMemoryReference>,
        totalMemories: Int,
        recentMessages: List<ChatMessageEntity>,
    ): ChatReply =
        if (supabase.auth.currentUserOrNull() == null) {
            ChatReply(content = buildLocalReply(query, references, totalMemories))
        } else {
            runCatching {
                fetchCloudReply(
                    query = query,
                    references = references,
                    recentMessages = recentMessages,
                )
            }.getOrElse {
                ChatReply(content = buildLocalReply(query, references, totalMemories))
            }
        }

    @OptIn(InternalAPI::class)
    private suspend fun fetchCloudReply(
        query: String,
        references: List<ChatMemoryReference>,
        recentMessages: List<ChatMessageEntity>,
    ): ChatReply {
        val response = supabase.functions.invoke("lulu-chat") {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            body = json.encodeToString(
                LuluChatRequest(
                    message = query,
                    memories = references.map { it.toRequestDto() },
                    recentMessages = recentMessages.map { it.toRequestMessageDto() },
                )
            )
        }
        val data = json.decodeFromString<LuluChatResponse>(response.bodyAsText())
        val reply = data.reply.trim()
        check(reply.isNotBlank()) { "AI 回复为空" }
        return ChatReply(content = reply)
    }

    private fun List<Memory>.rankFor(query: String): List<Memory> {
        val queryTerms = query.toSearchTerms()
        if (queryTerms.isEmpty()) {
            return sortedByDescending { it.updatedAt }.take(MAX_CONTEXT_MEMORIES)
        }
        return map { memory ->
            memory to memory.scoreFor(query = query, queryTerms = queryTerms)
        }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<Memory, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt },
            )
            .map { it.first }
    }

    private fun Memory.scoreFor(
        query: String,
        queryTerms: List<String>,
    ): Int {
        val haystack = listOf(title, content, mood, scene, tags.joinToString(" "))
            .joinToString(" ")
            .lowercase()
        val normalizedQuery = query.trim().lowercase()
        var score = 0
        if (normalizedQuery.length >= 2 && haystack.contains(normalizedQuery)) score += 12
        queryTerms.forEach { term ->
            if (title.lowercase().contains(term)) score += 6
            if (tags.any { it.lowercase().contains(term) }) score += 5
            if (content.lowercase().contains(term)) score += 3
            if (mood.lowercase().contains(term) || scene.lowercase().contains(term)) score += 2
        }
        return score
    }

    private fun String.toSearchTerms(): List<String> {
        val normalized = lowercase()
        val wordTerms = normalized
            .split(Regex("[\\s,，。！？!?；;：:、/\\\\()（）\\[\\]{}<>《》\"'`~]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
        val cjkTerms = normalized
            .filter { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
            .windowed(size = 2, step = 1, partialWindows = false)
        return (wordTerms + cjkTerms).distinct()
    }

    private fun Memory.toReference(): ChatMemoryReference =
        ChatMemoryReference(
            memoryId = id,
            title = title.ifBlank { "未命名记忆" }.take(48),
            excerpt = content
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(96)
                ?: "没有正文",
        )

    private fun buildLocalReply(
        query: String,
        references: List<ChatMemoryReference>,
        totalMemories: Int,
    ): String {
        if (totalMemories == 0) {
            return "我还没有可参考的记忆。你可以先在记忆页写下几条偏好、片段或想法；之后再来问我，我就能把回答贴近你的真实上下文。"
        }
        if (references.isEmpty()) {
            return "我暂时没有找到和「${query.take(24)}」直接相关的记忆。你可以换个更具体的关键词，或者先把这件事记录成一条记忆，我会在后续聊天里用上它。"
        }

        val context = references.joinToString(separator = "\n") { reference ->
            "· ${reference.title}：${reference.excerpt}"
        }
        return buildString {
            append("我参考了 ${references.size} 条记忆：\n")
            append(context)
            append("\n\n")
            append("基于这些线索，我会先把这件事放回你的长期语境里看：")
            append("如果你是在做选择，优先沿着已经反复出现的偏好走；")
            append("如果你是在整理想法，可以把新的判断再补成一条记忆，这样下次我能接着往前推。")
        }
    }

    private fun String.toConversationTitle(): String =
        lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(24)
            ?: "新的对话"

    private companion object {
        private const val MAX_CONTEXT_MEMORIES = 5
        private const val RECENT_HISTORY_LIMIT = 8
        private const val PREVIEW_LIMIT = 80
    }
}

private data class ChatReply(
    val content: String,
)

@Serializable
private data class LuluChatRequest(
    val message: String,
    val memories: List<LuluChatMemoryDto>,
    @SerialName("recent_messages") val recentMessages: List<LuluChatMessageDto>,
)

@Serializable
private data class LuluChatMemoryDto(
    @SerialName("memory_id") val memoryId: String,
    val title: String,
    val excerpt: String,
)

@Serializable
private data class LuluChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
private data class LuluChatResponse(
    val reply: String,
    val model: String = "",
)

private fun ChatMemoryReference.toRequestDto(): LuluChatMemoryDto =
    LuluChatMemoryDto(
        memoryId = memoryId,
        title = title,
        excerpt = excerpt,
    )

private fun ChatMessageEntity.toRequestMessageDto(): LuluChatMessageDto =
    LuluChatMessageDto(
        role = role.lowercase(),
        content = content.take(1200),
    )
