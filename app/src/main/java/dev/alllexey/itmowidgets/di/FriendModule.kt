package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.friendselector.data.DataStoreFriendSelectionHistory
import dev.alllexey.itmowidgets.feature.friendselector.data.FriendRepositoryImpl
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FriendModule {

    @Binds
    @Singleton
    abstract fun bindFriendRepository(
        impl: FriendRepositoryImpl
    ): FriendRepository

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindFriendRepositorySessionDataCleaner(
        impl: FriendRepositoryImpl
    ): SessionDataCleaner

    @Binds
    @Singleton
    abstract fun bindFriendSelectionHistory(
        impl: DataStoreFriendSelectionHistory
    ): FriendSelectionHistory

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindFriendHistorySessionDataCleaner(
        impl: DataStoreFriendSelectionHistory
    ): SessionDataCleaner
}
