package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.DefaultMarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import dev.alllexey.itmowidgets.feature.schedule.data.changes.DefaultScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler

/**
 * Koin to Hilt for what the 5 debug test entry points of the androidTests still read (`OnboardingTestEntryPoint`,
 * `NotificationDebugEntryPoint`, `MarksTestEntryPoint`, `ScheduleChangesTestEntryPoint`,
 * `SportSessionBindingsEntryPoint`); Koin constructs everything, Hilt has no other reader. KM-12c deletes this file
 * with the entry points.
 *
 * Unscoped on purpose: Koin owns the lifetime and returns its single every time. The trackings are read by their
 * implementation keys, so a debug fixture that overrides a contract in Koin never reaches Hilt. `ensureStarted`,
 * because Hilt can ask before `Application.onCreate()`.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugEntryPointsBridge {

    @Provides
    fun onboardingRepository(@ApplicationContext context: Context): OnboardingRepository = koin(context).get()

    @Provides
    fun appNotifier(@ApplicationContext context: Context): AppNotifier = koin(context).get()

    @Provides
    fun sessionTokenStore(@ApplicationContext context: Context): SessionTokenStore = koin(context).get()

    @Provides
    fun sessionRepository(@ApplicationContext context: Context): SessionRepository = koin(context).get()

    @Provides
    fun servicesOptInPreferences(@ApplicationContext context: Context): ServicesOptInPreferences =
        koin(context).get()

    @Provides
    fun deviceHintPreferences(@ApplicationContext context: Context): DeviceHintPreferences = koin(context).get()

    @Provides
    fun demoPreferences(@ApplicationContext context: Context): DemoPreferences = koin(context).get()

    @Provides
    fun markTracking(@ApplicationContext context: Context): MarkTracking = koin(context).get<DefaultMarkTracking>()

    @Provides
    fun marksScheduler(@ApplicationContext context: Context): MarksScheduler = koin(context).get()

    @Provides
    fun marksNotifier(@ApplicationContext context: Context): MarksNotifier = koin(context).get()

    @Provides
    fun scheduleChangeTracking(@ApplicationContext context: Context): ScheduleChangeTracking =
        koin(context).get<DefaultScheduleChangeTracking>()

    @Provides
    fun scheduleChangesScheduler(@ApplicationContext context: Context): ScheduleChangesScheduler =
        koin(context).get()

    @Provides
    fun scheduleChangeNotifier(@ApplicationContext context: Context): ScheduleChangeNotifier = koin(context).get()

    /** The cleaners sign-out runs, read again on every call. */
    @Provides
    fun sessionDataCleaners(@ApplicationContext context: Context): Set<@JvmSuppressWildcards SessionDataCleaner> =
        koin(context).getAll<SessionDataCleaner>().toSet()

    private fun koin(context: Context) = KoinStarter.ensureStarted(context)
}
