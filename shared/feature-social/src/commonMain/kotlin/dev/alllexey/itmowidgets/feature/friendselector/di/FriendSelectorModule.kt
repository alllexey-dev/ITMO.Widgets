package dev.alllexey.itmowidgets.feature.friendselector.di

import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.friendselector.data.DataStoreFriendSelectionHistory
import dev.alllexey.itmowidgets.feature.friendselector.data.FriendRepositoryImpl
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The schedule friend picker, data and sheet. The friend list is the picker's view of the one `SocialRepository`
 * and the people search is social's, both reached through their core ports (`socialModule` defines them); the
 * history is `recent_schedule_friends` in the `app_preferences` DataStore the platform provides. The
 * `SavedStateHandle` holds the host's `arg_selected_isu`.
 */
val friendSelectorModule = module {
    singleOf(::FriendRepositoryImpl) { bind<FriendRepository>() }
    singleOf(::DataStoreFriendSelectionHistory) { bind<FriendSelectionHistory>() }
    single<SessionDataCleaner>(named("friend-history")) { get<DataStoreFriendSelectionHistory>() }

    viewModelOf(::FriendSelectorViewModel)
}
