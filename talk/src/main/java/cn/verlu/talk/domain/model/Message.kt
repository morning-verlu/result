package cn.verlu.talk.domain.model

enum class MessageType { TEXT, IMAGE, LOCATION }

data class Message(
    val id: String,
    val roomId: String,
    val senderId: String,
    val content: String,
    val type: MessageType,
    val createdAtMs: Long,
    val isDeleted: Boolean,
    val senderProfile: Profile? = null,
)
