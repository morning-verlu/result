package cn.verlu.lulu.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.verlu.lulu.data.local.dao.ChatDao
import cn.verlu.lulu.data.local.dao.MemoryEntryDao
import cn.verlu.lulu.data.local.entity.ChatConversationEntity
import cn.verlu.lulu.data.local.entity.ChatMessageEntity
import cn.verlu.lulu.data.local.entity.MemoryEntryEntity

@Database(
    entities = [
        MemoryEntryEntity::class,
        ChatConversationEntity::class,
        ChatMessageEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class LuluDatabase : RoomDatabase() {
    abstract fun memoryEntryDao(): MemoryEntryDao
    abstract fun chatDao(): ChatDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN mood TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN scene TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN ownerId TEXT")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN lastSyncAttemptAt INTEGER")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN syncError TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_ownerId ON memory_entries(ownerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_syncStatus ON memory_entries(syncStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_updatedAt ON memory_entries(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_deletedAt ON memory_entries(deletedAt)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_conversations (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        lastMessagePreview TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        messageCount INTEGER NOT NULL,
                        memoryContextCount INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id TEXT NOT NULL,
                        conversationId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        referencedMemoriesJson TEXT NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(conversationId) REFERENCES chat_conversations(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_conversations_updatedAt ON chat_conversations(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId ON chat_messages(conversationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_createdAt ON chat_messages(createdAt)")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
