package dev.alllexey.itmowidgets.feature.social.di

import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.social.data.PeopleSearchRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.PersonRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.SocialRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.home.SocialHomeCardSource
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The friend-requests card's place in the open set of `HomeCardSource`s. */
val socialCardsQualifier: Qualifier = named("social")

/**
 * The social data and screens. Koin is the only graph for these types: one `SocialRepositoryImpl` serves the
 * screens, the home card, the session cleaners, the picker (through `FriendRepository`) and, through the app's
 * `SocialBridge`, the Hilt code that still injects it (the push handler, the profile tab, sport). The core types
 * (`BackendGate`, Core 2.0's `UsersApi` and `FriendsApi`, `MyItmoClient`, the application `CoroutineScope`,
 * `DemoMode`, `AppDispatchers`, the services opt-in, the current user) come from the platform (`CoreBridge` on
 * Android), the teacher reviews from `ReviewsBridge`. The `SavedStateHandle` holds the host's arguments
 * (`user_isu`, `user_name`) under the same keys. The picker has its own module (`friendSelectorModule`): features
 * never import each other, even inside one Gradle module.
 */
val socialModule = module {
    // One instance each; the cleaner contributions are qualified forwards (an open set).
    singleOf(::SocialRepositoryImpl) { bind<SocialRepository>() }
    single<SessionDataCleaner>(named("social")) { get<SocialRepositoryImpl>() }
    singleOf(::PersonRepositoryImpl) { bind<PersonRepository>() }
    single<SessionDataCleaner>(named("person")) { get<PersonRepositoryImpl>() }
    singleOf(::PeopleSearchRepositoryImpl) { bind<PeopleSearchRepository>() }
    singleOf(::SocialHomeCardSource)
    single<HomeCardSource>(socialCardsQualifier) { get<SocialHomeCardSource>() }

    viewModelOf(::FriendsViewModel)
    viewModelOf(::UserSearchViewModel)
    viewModelOf(::UserFriendsViewModel)
    viewModelOf(::UserProfileViewModel)
}
