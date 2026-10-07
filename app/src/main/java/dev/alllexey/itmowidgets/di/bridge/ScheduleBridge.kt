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
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for what the schedule data takes from `:app`: Core 2.0's schedule area over the one `BackendClient`,
 * the Android notification of found changes and LT-1's WorkManager scheduler of the change check. iOS binds its own
 * (L18). Also the phone calendar sync for `CalendarSyncWorker`, whose repository and calendars stay on Hilt until
 * KM-12b.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleBridgeEntryPoint {
    /** Unscoped in Hilt over the one `BackendClient`, so each call returns the same area. */
    fun backendScheduleApi(): ScheduleApi
    fun scheduleChangeNotifier(): ScheduleChangeNotifier
    fun scheduleChangesScheduler(): ScheduleChangesScheduler

    /** The `@Singleton` behind `CalendarSync`; the worker runs it through its implementation type. */
    fun defaultCalendarSync(): DefaultCalendarSync

    companion object {
        fun from(context: Context): ScheduleBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ScheduleBridgeEntryPoint::class.java)
    }
}

/**
 * Lazy definitions: Koin starts before Hilt builds its component, so each one reads Hilt on first use. The notifier
 * and the scheduler are unscoped and stateless in Hilt, so Koin asks for a new one each time.
 */
val scheduleBridgeModule = module {
    single<ScheduleApi> { ScheduleBridgeEntryPoint.from(androidContext()).backendScheduleApi() }
    factory<ScheduleChangeNotifier> { ScheduleBridgeEntryPoint.from(androidContext()).scheduleChangeNotifier() }
    factory<ScheduleChangesScheduler> { ScheduleBridgeEntryPoint.from(androidContext()).scheduleChangesScheduler() }
    single<DefaultCalendarSync> { ScheduleBridgeEntryPoint.from(androidContext()).defaultCalendarSync() }
}

/**
 * Koin to Hilt for the schedule data `scheduleDataModule` constructs, which Hilt-built Android code still takes: the
 * change tracking (the debug tools, LT-1's background check set) and the launcher preview scenario
 * (`ScheduleSettingsPreview`, `DefaultWidgetPreviewFactory`); and for the widget snapshot store
 * `componentBindingsModule` constructs (the session effects). Unscoped on purpose: Koin owns the lifetime, so Hilt
 * and Koin readers share one tracking and one store. The tracking is read by its implementation key, so a debug
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
}
