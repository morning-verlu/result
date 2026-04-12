package cn.verlu.talk.domain.model

data class Conversation(
    val roomId: String,
    val peer: Profile,
    val lastMessage: Message?,
    val unreadCount: Int = 0,
)
