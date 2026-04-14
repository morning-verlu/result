package cn.verlu.cnchess.data.repository

import cn.verlu.cnchess.domain.model.ChessInvite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface InviteRepository {
    val incomingPendingInvites: StateFlow<List<ChessInvite>>
    val outgoingPendingInvites: StateFlow<List<ChessInvite>>
    val gameLaunchEvents: Flow<String>

    suspend fun refreshInvites()
    suspend fun subscribeInviteChanges()
    suspend fun unsubscribeInviteChanges()

    suspend fun sendInvite(toUserId: String)
    suspend fun rejectInvite(inviteId: String)
    suspend fun cancelInvite(inviteId: String)
    suspend fun acceptInvite(inviteId: String): String
}
