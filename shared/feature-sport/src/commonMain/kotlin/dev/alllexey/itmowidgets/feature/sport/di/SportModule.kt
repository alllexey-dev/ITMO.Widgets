package dev.alllexey.itmowidgets.feature.sport.di

import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.data.home.SportHomeCardSource
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignPushBooker
import dev.alllexey.itmowidgets.feature.sport.data.repository.PendingSportBookingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportActionRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScoreRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportSignPreferencesRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.UserSportRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportAutoSignFlow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSharedLessonResolver
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignFilterController
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignStateFactory
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.home.SportHomeCardRenderer
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The sport card's place in the open set of `HomeCardSource`s; each contributor has its own qualifier. */
val sportCardsQualifier: Qualifier = named("sport")

/**
 * Sport, data and screens. Koin is the only graph for these types; the app's FCM handlers, the schedule's home card
 * and widget and `MainActivity` read them through `SportBridge`. The core contracts (`MyItmoClient`, `SportApi`, the
 * academic and wall clocks, `DemoMode`, `BackendGate`, `AppDispatchers`, `AppDiagnostics`, the sport-sign
 * preferences, the application `CoroutineScope`, the schedule refresh contracts) come from the app's `CoreBridge`,
 * `FriendRepository` from `friendSelectorModule`, the opt-in from `settingsDataModule`, and the debug ports
 * (`SportLessonTemplateProvider`, `SportScoreOverrideSource`) from the platform (`SportBridge` on Android).
 */
val sportModule = module {
    // One instance per repository for the screens, the home card, the widgets and sign-out; the cleaner and home card
    // contributions forward to it under their own qualifiers (open sets).
    singleOf(::SportScoreRepositoryImpl) { bind<SportScoreRepository>() }
    singleOf(::SportDataRepositoryImpl) { bind<SportDataRepository>() }
    single<SessionDataCleaner>(named("sport-queues")) { get<SportDataRepositoryImpl>() }
    singleOf(::SportBookingRepositoryImpl) { bind<SportBookingRepository>() }
    single<SessionDataCleaner>(named("sport-bookings")) { get<SportBookingRepositoryImpl>() }
    singleOf(::SportScheduleRepositoryImpl) { bind<SportScheduleRepository>() }
    singleOf(::SportActionRepositoryImpl) { bind<SportActionRepository>() }
    singleOf(::SportSignPreferencesRepositoryImpl) { bind<SportSignPreferencesRepository>() }
    singleOf(::UserSportRepositoryImpl) { bind<UserSportRepository>() }
    singleOf(::PendingSportBookingsRepositoryImpl) { bind<PendingSportBookingsRepository>() }
    singleOf(::SportHomeCardSource)
    single<HomeCardSource>(qualifier = sportCardsQualifier) { get<SportHomeCardSource>() }
    singleOf(::SportSignPushBooker)

    // Unscoped as under Hilt: the holder, the sign screen and the auto-sign flow each hold their own delegate.
    factoryOf(::SportBookingDelegate)
    factoryOf(::SportAutoSignFlow)
    factoryOf(::SportSignFilterController)
    factoryOf(::SportSignStateFactory)
    factoryOf(::SportSharedLessonResolver)
    // One holder for the tab, the feed and the schedule; it outlives every screen.
    singleOf(::SportBookingsHolder)
    viewModelOf(::SportMyViewModel)
    viewModelOf(::SportSignViewModel)
    viewModelOf(::UserSportViewModel)
    single<HomeCardRenderer>(named("sport")) { SportHomeCardRenderer(get<AcademicTimeProvider>().timeZone) }
}
