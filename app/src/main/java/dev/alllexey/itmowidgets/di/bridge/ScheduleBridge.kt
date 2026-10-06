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
import dev.alllexey.itmowidgets.feature.schedule.data.changes.DefaultScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesCheck
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetDataProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for what the schedule data takes from `:app`: Core 2.0's schedule area over the one `BackendClient`,
 * the Android notification of found changes and LT-1's WorkManager scheduler of the change check. iOS binds its own
 * (L18).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleBridgeEntryPoint {
    /** Unscoped in Hilt over the one `BackendClient`, so each call returns the same area. */
    fun backendScheduleApi(): ScheduleApi
    fun scheduleChangeNotifier(): ScheduleChangeNotifier
    fun scheduleChangesScheduler(): ScheduleChangesScheduler

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
}

/**
 * Koin to Hilt for the schedule data `scheduleDataModule` constructs, which Hilt-built Android code still takes: the
 * change tracking (the debug tools, LT-1's background check set), the change check (`ScheduleChangesWorker`), the
 * widget data provider (`ScheduleWidgetEntryPoint`) and the launcher preview scenario (`ScheduleSettingsPreview`,
 * `DefaultWidgetPreviewFactory`). Unscoped on purpose: Koin owns the lifetime, so Hilt and Koin readers share one
 * tracking. The tracking is read by its implementation key, so a debug fixture that overrides a contract in Koin
 * never reaches the background check set. `ensureStarted`, because a widget broadcast or a worker can run before
 * `Application.onCreate()`.
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
    fun scheduleChangesCheck(@ApplicationContext context: Context): ScheduleChangesCheck =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun scheduleWidgetDataProvider(@ApplicationContext context: Context): ScheduleWidgetDataProvider =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun schedulePreviewScenario(@ApplicationContext context: Context): SchedulePreviewScenario =
        KoinStarter.ensureStarted(context).get()
}
