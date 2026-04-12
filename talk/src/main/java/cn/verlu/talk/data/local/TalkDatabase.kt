package cn.verlu.talk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.verlu.talk.data.local.dao.ConversationDao
import cn.verlu.talk.data.local.dao.FriendshipDao
import cn.verlu.talk.data.local.dao.MessageDao
import cn.verlu.talk.data.local.entity.ConversationEntity
import cn.verlu.talk.data.local.entity.FriendshipEntity
import cn.verlu.talk.data.local.entity.MessageEntity

@Database(
    entities = [MessageEntity::class, ConversationEntity::class, FriendshipEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TalkDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun friendshipDao(): FriendshipDao
}
