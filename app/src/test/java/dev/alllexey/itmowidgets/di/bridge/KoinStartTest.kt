package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.app.WidgetRefreshCoordinator
import dev.alllexey.itmowidgets.core.debug.DefaultSportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.demo.DemoCurrentUserProvider
import dev.alllexey.itmowidgets.core.diagnostics.FileAppDiagnostics
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.DefaultAcademicTimeProvider
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import org.junit.Assert.assertNotSame
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertNotNull
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.Kind
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

/**
 * Boots the real Application with Koin, in both distribution flavors (the github and play unit-test
 * tasks each run it). Every test creates a new Application in the same JVM, so each one is also a second boot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class KoinStartTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the application boots with Koin`() {
        val application = bootApplication()

        val koin = GlobalContext.get()
        assertSame(application, koin.get<Context>())
        assertSame(koin.get<FileAppDiagnostics>(), koin.get<AppDiagnostics>())
    }

    @Test
    fun `a type bound under several keys is one instance`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<MyItmoStorage>(), koin.get<TokenStorage>())
        assertSame(koin.get<MyItmoStorage>(), koin.get<SessionTokenStore>())
        assertSame(koin.get<DefaultAcademicTimeProvider>(), koin.get<AcademicTimeProvider>())
        assertSame(koin.get<DefaultAcademicTimeProvider>(), koin.get<AcademicTimeOverrideController>())
        assertSame(koin.get<WidgetRefreshCoordinator>(), koin.get<WidgetRefreshRequester>())
        assertSame(koin.get<WidgetRefreshCoordinator>(), koin.get<ScheduleWidgetRefreshRequester>())
        assertSame(koin.get<DemoCurrentUserProvider>(), koin.get<CurrentUserProvider>())
        assertSame(koin.get<DefaultSportScoreOverrideProvider>(), koin.get<SportScoreOverrideController>())
        // A factory: the export keeps no state, so every reader gets a new one.
        assertNotSame(koin.get<ScheduleIcsExport>(), koin.get<ScheduleIcsExport>())
    }

    /** `verify()` reads constructors only; this builds every single the way the app does, lambdas included. */
    @OptIn(KoinInternalApi::class)
    @Test
    fun `every single of the release graph builds on the application`() {
        bootApplication()
        val koin = GlobalContext.get()

        koin.instanceRegistry.instances.values
            .map { it.beanDefinition }
            .filter { it.kind == Kind.Singleton }
            .distinct()
            .forEach { definition -> assertNotNull(koin.get<Any>(definition.primaryType, definition.qualifier)) }
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
     * `FcmWork.syncToken` with "WorkManager is not initialized", after Koin started.
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
