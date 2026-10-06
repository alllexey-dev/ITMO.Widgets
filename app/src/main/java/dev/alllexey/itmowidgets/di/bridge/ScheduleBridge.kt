package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import dev.alllexey.itmowidgets.feature.schedule.data.changes.DefaultScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for what the schedule data takes from `:app`: Core 2.0's schedule area over the one `BackendClient`,
 * the Android notification of found changes, the phone's calendars (`AndroidPhoneCalendars`) and LT-1's WorkManager
 * schedulers of the change check and the calendar sync. iOS binds its own (L18).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleBridgeEntryPoint {
    /** Unscoped in Hilt over the one `BackendClient`, so each call returns the same area. */
    fun backendScheduleApi(): ScheduleApi
    fun scheduleChangeNotifier(): ScheduleChangeNotifier
    fun scheduleChangesScheduler(): ScheduleChangesScheduler
    fun phoneCalendars(): PhoneCalendars
    fun calendarSyncScheduler(): CalendarSyncScheduler

    companion object {
        fun from(context: Context): ScheduleBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ScheduleBridgeEntryPoint::class.java)
    }
}

/**
 * Lazy definitions: Koin starts before Hilt builds its component, so each one reads Hilt on first use. The notifier,
 * the phone's calendars and the schedulers are unscoped and stateless in Hilt, so Koin asks for a new one each time.
 */
val scheduleBridgeModule = module {
    single<ScheduleApi> { ScheduleBridgeEntryPoint.from(androidContext()).backendScheduleApi() }
    factory<ScheduleChangeNotifier> { ScheduleBridgeEntryPoint.from(androidContext()).scheduleChangeNotifier() }
    factory<ScheduleChangesScheduler> { ScheduleBridgeEntryPoint.from(androidContext()).scheduleChangesScheduler() }
    factory<PhoneCalendars> { ScheduleBridgeEntryPoint.from(androidContext()).phoneCalendars() }
    factory<CalendarSyncScheduler> { ScheduleBridgeEntryPoint.from(androidContext()).calendarSyncScheduler() }
}

/**
 * Koin to Hilt for the schedule data `scheduleDataModule` constructs, which Hilt-built Android code still takes: the
 * Koin to Hilt for the schedule data `scheduleDataModule` constructs, which Hilt-built Android code still takes: the
 * change tracking (the debug tools, LT-1's background check set), the launcher preview scenario
 * (`ScheduleSettingsPreview`, `DefaultWidgetPreviewFactory`), the calendar sync (LT-1's background check set) and the
 * own schedule source (`IcsFileExport`); and for the widget snapshot store `componentBindingsModule` constructs (the
 * session effects). Unscoped on purpose: Koin owns the lifetime, so Hilt and Koin readers share one tracking, one
 * calendar sync and one store. The tracking and the calendar sync are read by their implementation keys, so a debug
 * fixture that overrides a contract in Koin never reaches the background check set. `ensureStarted`, because Hilt
 * can build a reader before `Application.onCreate()` has started Koin.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScheduleBridge {

    @Provides
    fun scheduleChangeTracking(@ApplicationContext context: Context): ScheduleChangeTracking =
        KoinStarter.ensureStarted(context).get<DefaultScheduleChangeTracking>()

    @Provides
    @IntoSet
    fun scheduleChangesBackgroundCheck(@ApplicationContext context: Context): BackgroundCheck =
        KoinStarter.ensureStarted(context).get<DefaultScheduleChangeTracking>()

    @Provides
    fun scheduleWidgetSnapshotStore(@ApplicationContext context: Context): ScheduleWidgetSnapshotStore =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun schedulePreviewScenario(@ApplicationContext context: Context): SchedulePreviewScenario =
        KoinStarter.ensureStarted(context).get()

    @Provides
    @IntoSet
    fun calendarSyncBackgroundCheck(@ApplicationContext context: Context): BackgroundCheck =
        KoinStarter.ensureStarted(context).get<DefaultCalendarSync>()

    @Provides
    fun ownScheduleSource(@ApplicationContext context: Context): OwnScheduleSource =
        KoinStarter.ensureStarted(context).get()
}
