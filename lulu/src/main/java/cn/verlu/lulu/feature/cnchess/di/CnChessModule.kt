package cn.verlu.lulu.feature.cnchess.di

import cn.verlu.lulu.feature.cnchess.data.repository.FriendRepository
import cn.verlu.lulu.feature.cnchess.data.repository.FriendRepositoryImpl
import cn.verlu.lulu.feature.cnchess.data.repository.GameRepository
import cn.verlu.lulu.feature.cnchess.data.repository.GameRepositoryImpl
import cn.verlu.lulu.feature.cnchess.data.repository.InviteRepository
import cn.verlu.lulu.feature.cnchess.data.repository.InviteRepositoryImpl
import cn.verlu.lulu.feature.cnchess.data.repository.PresenceRepository
import cn.verlu.lulu.feature.cnchess.data.repository.PresenceRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CnChessModule {
    @Provides
    @IoDispatcher
    fun provideCnChessIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

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
