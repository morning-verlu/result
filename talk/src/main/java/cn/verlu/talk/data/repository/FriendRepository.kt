package cn.verlu.talk.data.repository

import cn.verlu.talk.domain.model.Friendship
import cn.verlu.talk.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    /** Live stream of accepted friends from Room cache. */
    fun observeAcceptedFriends(): Flow<List<Friendship>>

    /** Live stream of pending requests addressed to current user from Room cache. */
    fun observePendingRequests(userId: String): Flow<List<Friendship>>

    /** Fetch friends + pending requests from Supabase and write to Room. */
    suspend fun refreshFriends()

    /** Subscribe to realtime friendship changes, updating Room on each event. */
    suspend fun subscribeToFriendshipChanges()
    suspend fun unsubscribeFromFriendshipChanges()

    suspend fun sendFriendRequest(addresseeId: String)
    suspend fun acceptFriendRequest(friendshipId: String)
    suspend fun rejectFriendRequest(friendshipId: String)
    suspend fun searchUser(query: String): Profile?
}
