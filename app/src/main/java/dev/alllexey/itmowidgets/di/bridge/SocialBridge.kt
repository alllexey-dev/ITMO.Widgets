package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the social data, which stays on Hilt until KM-11d. Hilt constructs each `@Singleton`, so the
 * screens, the session cleaners, the home card, the push handler and sport's friends share one instance (one graph per
 * binding). The teacher reviews come from `ReviewsBridge`, the current user from `CoreBridge`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SocialBridgeEntryPoint {
    fun socialRepository(): SocialRepository
    fun peopleSearchRepository(): PeopleSearchRepository
    fun friendRepository(): FriendRepository
    fun personRepository(): PersonRepository
    fun friendSelectionHistory(): FriendSelectionHistory

    companion object {
        fun from(context: Context): SocialBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, SocialBridgeEntryPoint::class.java)
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. */
val socialBridgeModule = module {
    single<SocialRepository> { SocialBridgeEntryPoint.from(androidContext()).socialRepository() }
    single<PeopleSearchRepository> { SocialBridgeEntryPoint.from(androidContext()).peopleSearchRepository() }
    single<FriendRepository> { SocialBridgeEntryPoint.from(androidContext()).friendRepository() }
    single<PersonRepository> { SocialBridgeEntryPoint.from(androidContext()).personRepository() }
    single<FriendSelectionHistory> { SocialBridgeEntryPoint.from(androidContext()).friendSelectionHistory() }
}
