package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository

/**
 * Koin to Hilt for the social data, which `socialModule` and `friendSelectorModule` construct: the friendship push
 * handler still injects [SocialRepository] from Hilt, sport's `SportDataRepositoryImpl` [FriendRepository]. Unscoped on purpose: Koin owns the lifetime and returns its single
 * every time, so the screens, the home card, the session cleaners and these readers share one repository.
 * `ensureStarted`, because an FCM message can arrive before `Application.onCreate()` finishes.
 */
@Module
@InstallIn(SingletonComponent::class)
object SocialBridge {

    @Provides
    fun socialRepository(@ApplicationContext context: Context): SocialRepository =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun friendRepository(@ApplicationContext context: Context): FriendRepository =
        KoinStarter.ensureStarted(context).get()
}
