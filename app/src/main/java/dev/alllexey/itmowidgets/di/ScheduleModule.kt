package dev.alllexey.itmowidgets.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.app.WidgetRefreshCoordinator
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.PeriodicCheckScheduler
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.AndroidPhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.CalendarSyncRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.IcsFileExport
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.MyItmoOwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import dev.alllexey.itmowidgets.feature.schedule.work.AndroidScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.work.CALENDAR_SYNC_SPEC
import dev.alllexey.itmowidgets.feature.schedule.work.SCHEDULE_CHANGES_SPEC
import javax.inject.Singleton

/**
 * The schedule's Android side: the phone calendar sync, the change notification and the WorkManager schedulers. The
 * schedule data lives in Koin (`scheduleDataModule`), the widget snapshot file too (`di/ComponentBindingsModule.kt`);
 * `ScheduleBridge` hands them to the Hilt readers (the session effects, the debug tools) and the notifier, the
 * scheduler and the calendar sync to Koin.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleModule {

    @Binds
    abstract fun bindScheduleWidgetRefreshRequester(
        impl: WidgetRefreshCoordinator
    ): ScheduleWidgetRefreshRequester

    @Binds
    abstract fun bindScheduleChangeNotifier(
        impl: AndroidScheduleChangeNotifier
    ): ScheduleChangeNotifier

    @Binds
    abstract fun bindPhoneCalendars(
        impl: AndroidPhoneCalendars
    ): PhoneCalendars

    @Binds
    abstract fun bindOwnScheduleSource(
        impl: MyItmoOwnScheduleSource
    ): OwnScheduleSource

    @Binds
    @Singleton
    abstract fun bindCalendarSyncRepository(
        impl: CalendarSyncRepositoryImpl
    ): CalendarSyncRepository

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindCalendarSyncSessionDataCleaner(
        impl: CalendarSyncRepositoryImpl
    ): SessionDataCleaner

    @Binds
    @Singleton
    abstract fun bindCalendarSync(
        impl: DefaultCalendarSync
    ): CalendarSync

    @Binds
    @IntoSet
    abstract fun bindCalendarSyncBackgroundCheck(
        impl: DefaultCalendarSync
    ): BackgroundCheck

    @Binds
    abstract fun bindScheduleIcsExport(
        impl: IcsFileExport
    ): ScheduleIcsExport

    companion object {
        @Provides
        fun scheduleChangesScheduler(@ApplicationContext context: Context): ScheduleChangesScheduler =
            object : ScheduleChangesScheduler, CheckScheduler by PeriodicCheckScheduler(context, SCHEDULE_CHANGES_SPEC) {}

        @Provides
        fun calendarSyncScheduler(@ApplicationContext context: Context): CalendarSyncScheduler =
            object : CalendarSyncScheduler, CheckScheduler by PeriodicCheckScheduler(context, CALENDAR_SYNC_SPEC) {}
    }
}
