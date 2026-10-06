package dev.alllexey.itmowidgets.feature.schedule.di

import dev.alllexey.itmowidgets.core.home.HomeCardSource
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
import dev.alllexey.itmowidgets.feature.schedule.data.changes.DefaultScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesCheck
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleChangesHomeCardSource
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleHomeCardSource
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetDataProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The today/tomorrow card's place in the open set of `HomeCardSource`s. */
val scheduleCardsQualifier: Qualifier = named("schedule")

/** The unread-changes card's place in the open set of `HomeCardSource`s. */
val scheduleChangesCardsQualifier: Qualifier = named("schedule-changes")

/**
 * The schedule data. Koin is the only graph for these types: one `ScheduleRepositoryImpl` serves the screens, the
 * home card, the widget, the subject page (`SubjectLessonsGateway`, `ScheduleRefreshGateway`) and sign-out; one
 * `ScheduleChangesRepositoryImpl` the change history, its home card, the background check and sign-out. Hilt-built
 * Android code (the workers, the widget entry point, the debug tools) takes them through the app's `ScheduleBridge`;
 * the calendar sync and the widget snapshot file stay in `:app`.
 *
 * From the platform: Core 2.0's `ScheduleApi`, the `ScheduleChangeNotifier` and `ScheduleChangesScheduler` (the
 * app's `ScheduleBridge` on Android, L18's module on iOS), `AppNotifier`, `MyItmoClient` and the other core contracts
 * (`CoreBridge`), `DemoMode` and the session token store, the services opt-in and the schedule preferences
 * (`settingsDataModule`), the pending sport rows (whichever graph sport owns). The selectors come from
 * [scheduleModule].
 */
val scheduleDataModule = module {
    // The stores have an internal test constructor beside the public one, so their references would be ambiguous.
    single<ScheduleLocalDataSource> { ScheduleLocalDataSourceImpl(get(), get<AppDirectories>(), get()) }
    singleOf(::ScheduleRemoteDataSourceImpl) { bind<ScheduleRemoteDataSource>() }
    // One cache and one access-revision table per process; the cleaner contributions are qualified forwards (an
    // open set the app merges into Hilt's sign-out set).
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
    // One run of the background check (iOS runs it from its own task, L18 IO-14).
    factoryOf(::ScheduleChangesCheck)

    singleOf(::ScheduleHomeCardSource)
    single<HomeCardSource>(qualifier = scheduleCardsQualifier) { get<ScheduleHomeCardSource>() }
    singleOf(::ScheduleChangesHomeCardSource)
    single<HomeCardSource>(qualifier = scheduleChangesCardsQualifier) { get<ScheduleChangesHomeCardSource>() }

    // Stateless: each widget update (Android) or timeline write (iOS, L18 IO-10b) gets its own.
    factoryOf(::ScheduleWidgetDataProvider)
}
