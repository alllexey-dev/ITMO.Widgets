package dev.alllexey.itmowidgets.feature.social.di

import dev.alllexey.itmowidgets.feature.social.presentation.FriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The social screens. Their data stays on the platform until KM-11d: the repositories come from the app's
 * `SocialBridge`, the teacher reviews from `ReviewsBridge`, the current user from `CoreBridge`. The `SavedStateHandle`
 * holds the host's arguments (`user_isu`, `user_name`) under the same keys. The picker has its own module
 * (`friendSelectorModule`): features never import each other, even inside one Gradle module.
 */
val socialModule = module {
    viewModelOf(::FriendsViewModel)
    viewModelOf(::UserSearchViewModel)
    viewModelOf(::UserFriendsViewModel)
    viewModelOf(::UserProfileViewModel)
}
