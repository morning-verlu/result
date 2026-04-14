package cn.verlu.cnchess.presentation.invite

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import cn.verlu.cnchess.domain.model.ChessInvite

@Composable
fun IncomingInviteDialog(
    invite: ChessInvite,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val senderName = invite.fromProfile?.displayName ?: "好友"
    AlertDialog(
        onDismissRequest = {},
        title = { Text("收到对局邀请") },
        text = { Text("$senderName 邀请你开始一盘中国象棋，现在应战吗？") },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("同意")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onReject) {
                Text("拒绝")
            }
        },
    )
}
