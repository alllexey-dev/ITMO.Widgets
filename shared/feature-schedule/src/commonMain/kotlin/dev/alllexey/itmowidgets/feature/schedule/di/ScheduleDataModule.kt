package dev.alllexey.itmowidgets.feature.schedule.di

import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.feature.schedule.data.LessonFriendsRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.SubjectLessonsGatewayImpl
import dev.alllexey.itmowidgets.feature.schedule.data.TeacherLessonsGatewayImpl
import dev.alllexey.itmowidgets.feature.schedule.data.TeacherWeeksFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.bell.ScheduleChangesBellSource
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.CalendarSyncFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.CalendarSyncRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.MyItmoOwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.data.changes.DefaultScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesCheck
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.today.DayLessonsGatewayImpl
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetDataProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The found changes' place in the open set of `BellSource`s. */
val scheduleChangesBellQualifier: Qualifier = named("schedule-changes")

/**
 * The schedule data. Koin is the only graph for these types: one `ScheduleRepositoryImpl` serves the screens, Сегодня
 * (`DayLessonsGateway`), the widget, the subject page (`SubjectLessonsGateway`, `ScheduleRefreshGateway`) and sign-out; one
 * `ScheduleChangesRepositoryImpl` the lessons' change marks, the bell, the background check and sign-out; one
 * `CalendarSyncRepositoryImpl` and one `DefaultCalendarSync` the phone calendar sync. The `.ics` export and the widget
 * snapshot file stay in `:app`.
 *
 * From the platform: Core 2.0's `ScheduleApi`, the `ScheduleChangeNotifier` and `ScheduleChangesScheduler`, the
 * phone's `PhoneCalendars` and the `CalendarSyncScheduler` (`androidScheduleModule` on Android, the iOS modules on
 * iOS), `AppNotifier`, `MyItmoClient`, `BuildingDirectory` and the other core contracts, `DemoMode`
 * and the session token store, the services opt-in and the schedule preferences (`settingsDataModule`), the pending
 * sport rows (whichever graph sport owns). The selectors come from [scheduleModule].
 */
val scheduleDataModule = module {
    // The stores have an internal test constructor beside the public one, so their references would be ambiguous.
    single<ScheduleLocalDataSource> { ScheduleLocalDataSourceImpl(get(), get<AppDirectories>(), get()) }
    singleOf(::ScheduleRemoteDataSourceImpl) { bind<ScheduleRemoteDataSource>() }
    // One cache and one access-revision table per process; the cleaner contributions are qualified forwards (an
    // open set sign-out collects with getAll).
    singleOf(::ScheduleRepositoryImpl) {
        bind<ScheduleRepository>()
        bind<ScheduleRefreshGateway>()
    }
    single<SessionDataCleaner>(named("schedule")) { get<ScheduleRepositoryImpl>() }
    singleOf(::LessonFriendsRepositoryImpl) { bind<LessonFriendsRepository>() }
    singleOf(::SubjectLessonsGatewayImpl) { bind<SubjectLessonsGateway>() }

    factory { TeacherWeeksFileStore(get<AppDirectories>()) }
    singleOf(::TeacherLessonsGatewayImpl) { bind<TeacherLessonsGateway>() }
    single<SessionDataCleaner>(named("schedule-teacher-lessons")) { get<TeacherLessonsGatewayImpl>() }

    factory { ScheduleChangesFileStore(get<AppDirectories>()) }
    singleOf(::ScheduleChangesRepositoryImpl) { bind<ScheduleChangesRepository>() }
    single<SessionDataCleaner>(named("schedule-changes")) { get<ScheduleChangesRepositoryImpl>() }
    singleOf(::DefaultScheduleChangeTracking) { bind<ScheduleChangeTracking>() }
    // One run of the background check (iOS runs it from its own task).
    factoryOf(::ScheduleChangesCheck)

    singleOf(::DayLessonsGatewayImpl) { bind<DayLessonsGateway>() }
    single<BellSource>(qualifier = scheduleChangesBellQualifier) { ScheduleChangesBellSource(get()) }

    // The phone calendar sync: the platform supplies `PhoneCalendars` and `CalendarSyncScheduler`.
    factory { CalendarSyncFileStore(get<AppDirectories>()) }
    factoryOf(::MyItmoOwnScheduleSource) { bind<OwnScheduleSource>() }
    singleOf(::CalendarSyncRepositoryImpl) { bind<CalendarSyncRepository>() }
    single<SessionDataCleaner>(named("calendar-sync")) { get<CalendarSyncRepositoryImpl>() }
    singleOf(::DefaultCalendarSync) { bind<CalendarSync>() }

    // Stateless: each widget update (Android) or timeline write (iOS) gets its own.
    factoryOf(::ScheduleWidgetDataProvider)
}
