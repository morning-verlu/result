package cn.verlu.lulu.feature.talk.di

import android.content.Context
import androidx.room.Room
import cn.verlu.lulu.core.feature.LuluDatabaseNames
import cn.verlu.lulu.feature.talk.data.local.TalkDatabase
import cn.verlu.lulu.feature.talk.data.local.dao.ConversationDao
import cn.verlu.lulu.feature.talk.data.local.dao.FriendshipDao
import cn.verlu.lulu.feature.talk.data.local.dao.MessageDao
import cn.verlu.lulu.feature.talk.data.repository.FriendRepository
import cn.verlu.lulu.feature.talk.data.repository.FriendRepositoryImpl
import cn.verlu.lulu.feature.talk.data.repository.MessageRepository
import cn.verlu.lulu.feature.talk.data.repository.MessageRepositoryImpl
import cn.verlu.lulu.feature.talk.data.repository.ProfileRepository
import cn.verlu.lulu.feature.talk.data.repository.ProfileRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object TalkModule {
    @Provides
    @IoDispatcher
    fun provideTalkIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideTalkDatabase(@ApplicationContext context: Context): TalkDatabase =
        Room.databaseBuilder(context, TalkDatabase::class.java, LuluDatabaseNames.TALK)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideMessageDao(db: TalkDatabase): MessageDao = db.messageDao()

    @Provides
    @Singleton
    fun provideConversationDao(db: TalkDatabase): ConversationDao = db.conversationDao()

    @Provides
    @Singleton
    fun provideFriendshipDao(db: TalkDatabase): FriendshipDao = db.friendshipDao()

    @Provides
    @Singleton
    fun provideProfileRepository(
        supabase: SupabaseClient,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): ProfileRepository = ProfileRepositoryImpl(supabase, dispatcher)

    @Provides
    @Singleton
    fun provideFriendRepository(
        supabase: SupabaseClient,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        friendshipDao: FriendshipDao,
    ): FriendRepository = FriendRepositoryImpl(supabase, dispatcher, friendshipDao)

    @Provides
    @Singleton
    fun provideMessageRepository(
        supabase: SupabaseClient,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        messageDao: MessageDao,
        conversationDao: ConversationDao,
    ): MessageRepository = MessageRepositoryImpl(supabase, dispatcher, messageDao, conversationDao)
}
