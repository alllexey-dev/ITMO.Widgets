package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.feature.social.data.push.FriendshipPushHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.friendselector.data.DataStoreFriendSelectionHistory
import dev.alllexey.itmowidgets.feature.friendselector.data.FriendRepositoryImpl
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import dev.alllexey.itmowidgets.feature.social.data.PeopleSearchRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.SocialRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.home.SocialHomeCardSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialModule {

    @Binds
    @IntoSet
    abstract fun friendshipPush(impl: FriendshipPushHandler): FcmPayloadHandler

    @Binds
    @Singleton
    abstract fun bindSocialRepository(
        impl: SocialRepositoryImpl
    ): SocialRepository

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSocialRepositorySessionDataCleaner(
        impl: SocialRepositoryImpl
    ): SessionDataCleaner

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSocialHomeCards(
        impl: SocialHomeCardSource
    ): HomeCardSource

    @Binds
    @Singleton
    abstract fun bindPeopleSearchRepository(
        impl: PeopleSearchRepositoryImpl
    ): PeopleSearchRepository

    @Binds
    @Singleton
    abstract fun bindFriendRepository(
        impl: FriendRepositoryImpl
    ): FriendRepository

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
