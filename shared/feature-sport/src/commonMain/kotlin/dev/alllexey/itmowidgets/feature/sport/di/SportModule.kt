package dev.alllexey.itmowidgets.feature.sport.di

import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.feature.sport.data.bell.SportBellSource
import dev.alllexey.itmowidgets.feature.sport.data.bell.SportResultLog
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignPushBooker
import dev.alllexey.itmowidgets.feature.sport.data.repository.PendingSportBookingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportActionRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScoreRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportSectionRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportSignPreferencesRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.UserSportRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSectionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSectionsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.points.SportPointsViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportAutoSignFlow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSharedLessonResolver
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportViewModel
import org.koin.core.module.dsl.bind
import dev.alllexey.itmowidgets.feature.sport.data.widget.SportWidgetDataProvider
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSelector
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The queue results' place in the open set of `BellSource`s; each contributor has its own qualifier. */
val sportBellQualifier: Qualifier = named("sport")

/**
 * Sport, data and screens. Koin is the only graph for these types. The core contracts (`MyItmoClient`, `SportApi`,
 * the academic and wall clocks, `DemoMode`, `BackendGate`, `AppDispatchers`, `AppDiagnostics`, the sport-sign
 * preferences, the application `CoroutineScope`, the schedule refresh contracts) come from the platform's Koin
 * modules, `FriendRepository` from `socialModule`, the opt-in from `settingsDataModule`, and the debug ports
 * (`SportLessonTemplateProvider`, `SportScoreOverrideSource`) from the platform (`androidSportModule` on Android), and
 * `DayLessonsGateway` from the schedule's module for the lessons a sport lesson clashes with.
 */
val sportModule = module {
    // One instance per repository for the screens, Сегодня, the widgets and sign-out; the cleaner and bell
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
    singleOf(::SportSectionRepositoryImpl) { bind<SportSectionRepository>() }
    single<SessionDataCleaner>(named("sport-sections")) { get<SportSectionRepositoryImpl>() }
    singleOf(::PendingSportBookingsRepositoryImpl) { bind<PendingSportBookingsRepository>() }
    // The log has an internal test constructor beside the public one, so its reference would be ambiguous.
    single { SportResultLog(get<AppDirectories>(), get(), get()) }
    single<SessionDataCleaner>(named("sport-results")) { get<SportResultLog>() }
    single<BellSource>(qualifier = sportBellQualifier) { SportBellSource(get(), getOrNull()) }
    singleOf(::SportSignPushBooker)

    // Unscoped: the holder, the sign screen and the auto-sign flow each hold their own delegate.
    factoryOf(::SportBookingDelegate)
    factoryOf(::SportAutoSignFlow)
    factoryOf(::SportSharedLessonResolver)
    // One holder for the tab, the feed and the schedule; it outlives every screen.
    singleOf(::SportBookingsHolder)
    factoryOf(::SportWidgetSelector)
    factoryOf(::SportWidgetDataProvider)
    singleOf(::SportSectionsHolder)
    viewModelOf(::SportMyViewModel)
    viewModelOf(::SportSignViewModel)
    viewModelOf(::SportPointsViewModel)
    viewModelOf(::UserSportViewModel)
}
