package cn.verlu.lulu.feature.cnchess.data.repository

import cn.verlu.lulu.feature.cnchess.domain.model.Friendship
import kotlinx.coroutines.flow.StateFlow

interface FriendRepository {
    val friends: StateFlow<List<Friendship>>
    suspend fun refreshFriends()
    suspend fun subscribeToFriendshipChanges()
    suspend fun unsubscribeFromFriendshipChanges()
}
