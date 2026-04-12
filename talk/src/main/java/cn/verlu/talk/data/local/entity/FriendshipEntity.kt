package cn.verlu.talk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import cn.verlu.talk.domain.model.Friendship
import cn.verlu.talk.domain.model.FriendStatus
import cn.verlu.talk.domain.model.Profile

@Entity(tableName = "friendships")
data class FriendshipEntity(
    @PrimaryKey val id: String,
    val requesterId: String,
    val addresseeId: String,
    val status: String,
    val createdAtMs: Long,
    val roomId: String?,
    val requesterDisplayName: String?,
    val requesterAvatarUrl: String?,
    val addresseeDisplayName: String?,
    val addresseeAvatarUrl: String?,
)

fun FriendshipEntity.toDomain(): Friendship = Friendship(
    id = id,
    requesterId = requesterId,
    addresseeId = addresseeId,
    status = when (status) {
        "accepted" -> FriendStatus.ACCEPTED
        "blocked" -> FriendStatus.BLOCKED
        else -> FriendStatus.PENDING
    },
    createdAtMs = createdAtMs,
    roomId = roomId,
    requesterProfile = if (requesterDisplayName != null) Profile(
        id = requesterId,
        displayName = requesterDisplayName,
        avatarUrl = requesterAvatarUrl,
    ) else null,
    addresseeProfile = if (addresseeDisplayName != null) Profile(
        id = addresseeId,
        displayName = addresseeDisplayName,
        avatarUrl = addresseeAvatarUrl,
    ) else null,
)

fun Friendship.toEntity(): FriendshipEntity = FriendshipEntity(
    id = id,
    requesterId = requesterId,
    addresseeId = addresseeId,
    status = status.name.lowercase(),
    createdAtMs = createdAtMs,
    roomId = roomId,
    requesterDisplayName = requesterProfile?.displayName,
    requesterAvatarUrl = requesterProfile?.avatarUrl,
    addresseeDisplayName = addresseeProfile?.displayName,
    addresseeAvatarUrl = addresseeProfile?.avatarUrl,
)
