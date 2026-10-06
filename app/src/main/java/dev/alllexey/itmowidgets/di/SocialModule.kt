package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.feature.social.data.push.FriendshipPushHandler

/**
 * The friendship push handler, which stays Android. The social data lives in Koin (`socialModule`,
 * `friendSelectorModule`); the handler reads its `SocialRepository` through `SocialBridge`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SocialModule {

    @Binds
    @IntoSet
    abstract fun friendshipPush(impl: FriendshipPushHandler): FcmPayloadHandler
}
