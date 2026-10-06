package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

/**
 * Boots the real `@HiltAndroidApp` Application with Koin, in both distribution flavors (the github and play unit-test
 * tasks each run it). Every test creates a new Application in the same JVM, so each one is also a second boot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class KoinStartTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the application boots with Koin and Hilt`() {
        val application = bootApplication()

        val koin = GlobalContext.get()
        assertSame(application, koin.get<Context>())
        assertSame(application.diagnostics, koin.get<AppDiagnostics>())
    }

    @Test
    fun `core contracts resolve in Koin to the instances Hilt builds`() {
        val application = bootApplication()
        val hilt = CoreBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.academicTimeProvider(), koin.get<AcademicTimeProvider>())
        assertSame(hilt.demoMode(), koin.get<DemoMode>())
        assertSame(hilt.appDiagnostics(), koin.get<AppDiagnostics>())
        assertSame(hilt.backendGate(), koin.get<BackendGate>())
        assertSame(hilt.appDispatchers(), koin.get<AppDispatchers>())
        assertSame(hilt.sessionRepository(), koin.get<SessionRepository>())
        assertSame(hilt.currentUserProvider(), koin.get<CurrentUserProvider>())
        assertSame(hilt.clock(), koin.get<Clock>())
        assertSame(hilt.myItmoClient(), koin.get<MyItmoClient>())
        assertSame(hilt.appPreferences(), koin.get<DataStore<Preferences>>())
        assertSame(hilt.appDirectories(), koin.get<AppDirectories>())
        assertSame(hilt.qrSettingsPreferences(), koin.get<QrSettingsPreferences>())
        assertSame(hilt.deviceHintPreferences(), koin.get<DeviceHintPreferences>())
        assertSame(hilt.homeLayoutPreferences(), koin.get<HomeLayoutPreferences>())
        assertSame(hilt.servicesOptInPreferences(), koin.get<ServicesOptInPreferences>())
        assertSame(hilt.scheduleCheckPreferences(), koin.get<ScheduleCheckPreferences>())
        assertSame(hilt.widgetSettingsPreferences(), koin.get<WidgetSettingsPreferences>())
        assertSame(hilt.sportSignSelectorPreferences(), koin.get<SportSignSelectorPreferences>())
        assertSame(hilt.markSourcePreferences(), koin.get<MarkSourcePreferences>())
        assertSame(hilt.usersApi(), koin.get<UsersApi>())
        assertSame(hilt.backendIdentitySync(), koin.get<BackendIdentitySync>())
        assertSame(hilt.backendDeviceSession(), koin.get<BackendDeviceSession>())
        assertSame(hilt.fcmTokenSync(), koin.get<FcmTokenSync>())
        assertSame(hilt.customSpoilerRepository(), koin.get<CustomSpoilerRepository>())
        assertSame(hilt.onboardingRepository(), koin.get<OnboardingRepository>())
        assertSame(hilt.scheduleChangeTracking(), koin.get<ScheduleChangeTracking>())
        assertSame(hilt.markTracking(), koin.get<MarkTracking>())
        assertSame(hilt.coreCalendarSync(), koin.get<CalendarSync>())
        // Unscoped in Hilt, a factory in Koin: the same implementation, a new instance each time.
        assertEquals(hilt.scheduleIcsExport()::class, koin.get<ScheduleIcsExport>()::class)
        assertSame(hilt.subjectLessonsGateway(), koin.get<SubjectLessonsGateway>())
        assertSame(hilt.scheduleRefreshGateway(), koin.get<ScheduleRefreshGateway>())
        // Unscoped in Hilt: Koin keeps the first instance it gets and hands out that one.
        assertSame(koin.get<SportScoreRepository>(), koin.get<SportScoreRepository>())
        assertSame(hilt.pendingSportBookingsRepository(), koin.get<PendingSportBookingsRepository>())
        assertSame(hilt.teacherLessonsGateway(), koin.get<TeacherLessonsGateway>())
        assertSame(hilt.applicationScope(), koin.get<CoroutineScope>())
        assertSame(hilt.scheduleWidgetRefreshRequester(), koin.get<ScheduleWidgetRefreshRequester>())
    }

    @Test
    fun `a running graph is returned as is`() {
        val application = bootApplication()

        assertSame(GlobalContext.get(), KoinStarter.ensureStarted(application))
    }

    @Test
    fun `concurrent first calls start one graph`() {
        val application = bootApplication()
        stopKoin()
        val threads = 8
        val ready = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            val results = executor.invokeAll(
                List(threads) {
                    Callable<Koin> {
                        ready.countDown()
                        ready.await()
                        KoinStarter.ensureStarted(application)
                    }
                },
            ).map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1, results.distinct().size)
            assertSame(GlobalContext.get(), results.first())
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * Creates the Application. Robolectric runs no `androidx.startup` provider, so `onCreate()` stops at
     * `FcmWork.syncToken` with "WorkManager is not initialized", after Koin started and Hilt injected the fields.
     */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
