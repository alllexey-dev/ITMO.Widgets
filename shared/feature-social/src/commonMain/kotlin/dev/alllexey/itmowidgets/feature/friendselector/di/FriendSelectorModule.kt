package dev.alllexey.itmowidgets.feature.friendselector.di

import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The schedule friend picker. The friend list, the people search and the picker history stay on the platform until
 * KM-11d and come from the app's `SocialBridge`; the `SavedStateHandle` holds the host's `arg_selected_isu`.
 */
val friendSelectorModule = module {
    viewModelOf(::FriendSelectorViewModel)
}
