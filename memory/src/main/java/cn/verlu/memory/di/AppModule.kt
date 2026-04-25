package cn.verlu.memory.di

import android.content.Context
import cn.verlu.memory.data.repository.FileLifeStreamRepository
import cn.verlu.memory.data.remote.SupabaseConfig
import cn.verlu.memory.data.repository.SupabaseAuthRepository
import cn.verlu.memory.domain.repository.AuthRepository
import cn.verlu.memory.domain.repository.LifeStreamRepository
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
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

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
                scheme = "memoryapp"
                host = "login"
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Realtime)
            install(Functions)
            install(Storage)
            httpConfig {
                install(Logging) {
                    level = LogLevel.INFO
                }
            }
        }

    @Provides
    @Singleton
    fun provideAuthRepository(
        supabase: SupabaseClient,
    ): AuthRepository = SupabaseAuthRepository(supabase)

    @Provides
    @Singleton
    fun provideLifeStreamRepository(
        @ApplicationContext context: Context,
        supabase: SupabaseClient,
    ): LifeStreamRepository = FileLifeStreamRepository(context, supabase)
}
