package cn.verlu.lulu.presentation.chat

import cn.verlu.lulu.domain.chat.ChatConversation
import cn.verlu.lulu.domain.chat.ChatMessage

object ChatContract {
    data class UiState(
        val conversations: List<ChatConversation> = emptyList(),
        val selectedConversationId: String? = null,
        val messages: List<ChatMessage> = emptyList(),
        val input: String = "",
        val isLoading: Boolean = true,
        val isSending: Boolean = false,
        val errorMessage: String? = null,
    )

    sealed interface Intent {
        data object NewConversation : Intent
        data class SelectConversation(val conversationId: String) : Intent
        data class InputChanged(val value: String) : Intent
        data object SendMessage : Intent
        data class SaveAssistantMessage(val messageId: String) : Intent
        data object ClearError : Intent
    }

    sealed interface Effect {
        data class ShowMessage(val message: String) : Effect
    }
}
