package cn.verlu.talk.domain.model

enum class FriendStatus { PENDING, ACCEPTED, BLOCKED }

data class Friendship(
    val id: String,
    val requesterId: String,
    val addresseeId: String,
    val status: FriendStatus,
    val createdAtMs: Long,
    val roomId: String? = null,
    val requesterProfile: Profile? = null,
    val addresseeProfile: Profile? = null,
) {
    fun peerProfile(currentUserId: String): Profile? =
        if (requesterId == currentUserId) addresseeProfile else requesterProfile
}
