package cn.verlu.cnchess.di

import cn.verlu.cnchess.data.repository.FriendRepository
import cn.verlu.cnchess.data.repository.FriendRepositoryImpl
import cn.verlu.cnchess.data.repository.GameRepository
import cn.verlu.cnchess.data.repository.GameRepositoryImpl
import cn.verlu.cnchess.data.repository.InviteRepository
import cn.verlu.cnchess.data.repository.InviteRepositoryImpl
import cn.verlu.cnchess.data.repository.PresenceRepository
import cn.verlu.cnchess.data.repository.PresenceRepositoryImpl
import cn.verlu.cnchess.data.remote.SupabaseConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
            supabaseKey = SupabaseConfig.ANON_KEY,
        ) {
            requestTimeout = 60.seconds
            install(Auth) {
                scheme = "cnchessapp"
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
    fun providePresenceRepository(
        impl: PresenceRepositoryImpl,
    ): PresenceRepository = impl

    @Provides
    @Singleton
    fun provideFriendRepository(
        impl: FriendRepositoryImpl,
    ): FriendRepository = impl

    @Provides
    @Singleton
    fun provideInviteRepository(
        impl: InviteRepositoryImpl,
    ): InviteRepository = impl

    @Provides
    @Singleton
    fun provideGameRepository(
        impl: GameRepositoryImpl,
    ): GameRepository = impl
}
