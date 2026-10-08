package dev.alllexey.itmowidgets.feature.schedule.di.calendar

import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.work.AppRefreshScheduler
import dev.alllexey.itmowidgets.core.work.RefreshStep
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.EventKitIdStore
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.EventKitPhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.EventStore
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.IosIcsFileExport
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.RefreshTaskCalendarScheduler
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.SystemEventStore
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The calendar on iOS (IO-15b), the ports `scheduleDataModule`'s sync and the settings' `.ics` sheet take from the
 * platform: the phone's calendars over EventKit, the app refresh task as the sync's scheduler and its runner step
 * under [RefreshStepKeys.CALENDAR_SYNC] with Android's two hours, and the `.ics` file in the temporary directory.
 * The background module gives the refresh task and its step log.
 */
val calendarIosModule = module {
    single { EventKitIdStore(get<AppDirectories>()) }
    single<EventStore> { SystemEventStore(get<AcademicTimeProvider>().timeZone.id) }
    single<PhoneCalendars> { EventKitPhoneCalendars(get(), get()) }
    single<CalendarSyncScheduler> { RefreshTaskCalendarScheduler(get<AppRefreshScheduler>(), get()) }
    single<ScheduleIcsExport> { IosIcsFileExport(get(), get(), get(), get()) }
    single(named(RefreshStepKeys.CALENDAR_SYNC)) {
        val sync = get<DefaultCalendarSync>()
        RefreshStep(RefreshStepKeys.CALENDAR_SYNC, RefreshStepKeys.CALENDAR_SYNC_PERIOD) { sync.run() }
    }
}
