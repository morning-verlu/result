package cn.verlu.cnchess.domain.model

data class Friendship(
    val id: String,
    val requesterId: String,
    val addresseeId: String,
    val roomId: String? = null,
    val requesterProfile: Profile? = null,
    val addresseeProfile: Profile? = null,
) {
    fun peerProfile(currentUserId: String): Profile? =
        if (requesterId == currentUserId) addresseeProfile else requesterProfile
}
