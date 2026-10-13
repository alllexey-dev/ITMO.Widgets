package dev.alllexey.itmowidgets.feature.social.di

import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.social.data.PeopleSearchRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.PersonRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.SocialRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.bell.FriendRequestsBellSource
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

/** The friend requests' place in the open set of `BellSource`s. */
val socialBellQualifier: Qualifier = named("social")

/**
 * The social data and screens. Koin is the only graph for these types: one `SocialRepositoryImpl` serves the
 * screens, the bell, the session cleaners and the picker (through `FriendRepository`). The core types
 * (`BackendGate`, Core 2.0's `UsersApi` and `FriendsApi`, `MyItmoClient`, the application `CoroutineScope`,
 * `DemoMode`, `AppDispatchers`, the services opt-in, the current user) come from the platform's Koin
 * modules, the teacher reviews from `reviewsModule`. The `SavedStateHandle` holds the host's arguments
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
    single<BellSource>(socialBellQualifier) { FriendRequestsBellSource(get(), get()) }

    viewModelOf(::FriendsViewModel)
    viewModelOf(::UserSearchViewModel)
    viewModelOf(::UserFriendsViewModel)
    viewModelOf(::UserProfileViewModel)
}
