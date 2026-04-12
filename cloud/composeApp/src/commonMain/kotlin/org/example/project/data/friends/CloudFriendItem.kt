package cn.verlu.cloud.data.friends

/**
 * 当前登录用户的一个好友（来自 Talk 共享的 friendships 表）。
 * [roomId] 为双方的聊天室 ID，可直接向其发送消息。
 */
data class CloudFriendItem(
    val userId: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    /** 与当前用户的私聊室 ID，null 表示尚未创建聊天室（不可分享）。 */
    val roomId: String?,
)
