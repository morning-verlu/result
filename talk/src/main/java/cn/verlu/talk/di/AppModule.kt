package cn.verlu.talk.di

import android.content.Context
import androidx.room.Room
import cn.verlu.talk.data.local.TalkDatabase
import cn.verlu.talk.data.local.dao.ConversationDao
import cn.verlu.talk.data.local.dao.FriendshipDao
import cn.verlu.talk.data.local.dao.MessageDao
import cn.verlu.talk.data.remote.SupabaseConfig
import cn.verlu.talk.data.repository.FriendRepository
import cn.verlu.talk.data.repository.FriendRepositoryImpl
import cn.verlu.talk.data.repository.MessageRepository
import cn.verlu.talk.data.repository.MessageRepositoryImpl
import cn.verlu.talk.data.repository.ProfileRepository
import cn.verlu.talk.data.repository.ProfileRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @OptIn(SupabaseInternal::class)
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL,
            supabaseKey = SupabaseConfig.ANON_KEY
        ) {
            requestTimeout = 60.seconds
            install(Auth) {
                scheme = "talkapp"
                host = "login"
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Realtime)
            install(Functions)
            httpConfig {
                install(Logging) {
                    level = LogLevel.INFO
                }
            }
        }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideTalkDatabase(@ApplicationContext context: Context): TalkDatabase =
        Room.databaseBuilder(context, TalkDatabase::class.java, "talk.db")
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
