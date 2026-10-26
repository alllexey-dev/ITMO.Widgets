package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.app.WidgetRefreshCoordinator
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.PeriodicCheckScheduler
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.AndroidPhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.IcsFileExport
import dev.alllexey.itmowidgets.feature.schedule.data.changes.DefaultScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import dev.alllexey.itmowidgets.feature.schedule.work.AndroidScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.work.CALENDAR_SYNC_SPEC
import dev.alllexey.itmowidgets.feature.schedule.work.SCHEDULE_CHANGES_SPEC
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The schedule's Android side: the widget refresh, the phone's calendars (`CalendarContract`), the `.ics` export, the
 * change notification, the WorkManager schedulers and the two background checks. The schedule data and the calendar
 * sync are `scheduleDataModule`'s, the widget snapshot file `componentBindingsModule`'s.
 */
val androidScheduleModule = module {
    single { WidgetRefreshCoordinator(androidContext(), get()) } binds
        arrayOf(WidgetRefreshRequester::class, ScheduleWidgetRefreshRequester::class)
    factoryOf(::AndroidScheduleChangeNotifier) { bind<ScheduleChangeNotifier>() }
    factoryOf(::AndroidPhoneCalendars) { bind<PhoneCalendars>() }
    factory<ScheduleIcsExport> { IcsFileExport(androidContext(), get(), get(), get(), get()) }
    factory<ScheduleChangesScheduler> {
        object : ScheduleChangesScheduler, CheckScheduler by PeriodicCheckScheduler(androidContext(), SCHEDULE_CHANGES_SPEC) {}
    }
    factory<CalendarSyncScheduler> {
        object : CalendarSyncScheduler, CheckScheduler by PeriodicCheckScheduler(androidContext(), CALENDAR_SYNC_SPEC) {}
    }
    // The implementation keys, so a debug fixture that overrides a contract never reaches the background checks.
    single<BackgroundCheck>(named("schedule-changes")) { get<DefaultScheduleChangeTracking>() }
    single<BackgroundCheck>(named("calendar-sync")) { get<DefaultCalendarSync>() }
}
