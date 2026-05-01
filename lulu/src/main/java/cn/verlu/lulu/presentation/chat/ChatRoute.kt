package cn.verlu.lulu.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.lulu.domain.chat.ChatConversation
import cn.verlu.lulu.domain.chat.ChatMemoryReference
import cn.verlu.lulu.domain.chat.ChatMessage
import cn.verlu.lulu.domain.chat.ChatMessageRole
import cn.verlu.lulu.presentation.ui.SyncLoadingIndicator
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ChatRoute(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            if (effect is ChatContract.Effect.ShowMessage) {
                snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onIntent(ChatContract.Intent.ClearError)
    }

    Box(modifier = modifier.fillMaxSize()) {
        ChatScreen(
            state = state,
            onNewConversation = { viewModel.onIntent(ChatContract.Intent.NewConversation) },
            onSelectConversation = { viewModel.onIntent(ChatContract.Intent.SelectConversation(it)) },
            onInputChanged = { viewModel.onIntent(ChatContract.Intent.InputChanged(it)) },
            onSend = { viewModel.onIntent(ChatContract.Intent.SendMessage) },
            onSaveAssistantMessage = { viewModel.onIntent(ChatContract.Intent.SaveAssistantMessage(it)) },
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun ChatScreen(
    state: ChatContract.UiState,
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSaveAssistantMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatHeader(
            state = state,
            onNewConversation = onNewConversation,
        )
        ConversationStrip(
            conversations = state.conversations,
            selectedConversationId = state.selectedConversationId,
            onSelectConversation = onSelectConversation,
        )
        HorizontalDivider()
        MessageList(
            state = state,
            onSaveAssistantMessage = onSaveAssistantMessage,
            modifier = Modifier.weight(1f),
        )
        ChatInput(
            value = state.input,
            isSending = state.isSending,
            onValueChange = onInputChanged,
            onSend = onSend,
        )
    }
}

@Composable
private fun ChatHeader(
    state: ChatContract.UiState,
    onNewConversation: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.contextLine(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalButton(
            onClick = onNewConversation,
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("新对话", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun ConversationStrip(
    conversations: List<ChatConversation>,
    selectedConversationId: String?,
    onSelectConversation: (String) -> Unit,
) {
    if (conversations.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(conversations, key = { it.id }) { conversation ->
            ConversationChip(
                conversation = conversation,
                selected = conversation.id == selectedConversationId,
                onClick = { onSelectConversation(conversation.id) },
            )
        }
    }
}

@Composable
private fun ConversationChip(
    conversation: ChatConversation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .size(width = 184.dp, height = 78.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = conversation.lastMessagePreview,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${conversation.messageCount} 条 · ${conversation.memoryContextCount} 条记忆",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MessageList(
    state: ChatContract.UiState,
    onSaveAssistantMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SyncLoadingIndicator()
        }

        state.messages.isEmpty() -> EmptyChatState(modifier = modifier)

        else -> LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    onSaveAssistantMessage = onSaveAssistantMessage,
                )
            }
        }
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "问我一件和你记忆有关的事",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "我会从本地记忆里找上下文，并把引用过的记忆列出来。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onSaveAssistantMessage: (String) -> Unit,
) {
    val isUser = message.role == ChatMessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.84f else 0.94f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isUser) "你" else "Lulu",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = message.createdAt.chatTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (message.referencedMemories.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        message.referencedMemories.forEach { reference ->
                            MemoryReferenceChip(reference)
                        }
                    }
                }
                if (!isUser) {
                    Button(
                        onClick = { onSaveAssistantMessage(message.id) },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("存为记忆", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryReferenceChip(reference: ChatMemoryReference) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = reference.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun ChatInput(
    value: String,
    isSending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp, max = 132.dp),
            enabled = !isSending,
            placeholder = { Text("问 Lulu：我最近在想什么？") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            maxLines = 5,
        )
        IconButton(
            onClick = onSend,
            enabled = value.isNotBlank() && !isSending,
        ) {
            if (isSending) {
                SyncLoadingIndicator(modifier = Modifier.size(22.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

private fun ChatContract.UiState.contextLine(): String {
    val selected = conversations.firstOrNull { it.id == selectedConversationId }
    return when {
        selected == null && conversations.isEmpty() -> "本地记忆上下文已接入"
        selected == null -> "选择一段对话继续"
        selected.memoryContextCount > 0 -> "最近使用 ${selected.memoryContextCount} 条记忆作为上下文"
        else -> "这段对话还没有命中记忆"
    }
}

private fun java.time.Instant.chatTime(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
