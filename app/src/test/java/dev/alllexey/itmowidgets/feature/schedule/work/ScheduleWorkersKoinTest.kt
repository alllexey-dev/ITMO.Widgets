package dev.alllexey.itmowidgets.feature.schedule.work

import android.app.Application
import androidx.work.ListenableWorker.Result
import androidx.work.testing.WorkManagerTestInitHelper
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.core.work.buildWorker
import dev.alllexey.itmowidgets.core.work.startWorkerGraph
import dev.alllexey.itmowidgets.core.work.unusedPort
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesCheck
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The schedule's workers read their dependencies from Koin (KM-12a), built as WorkManager builds them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScheduleWorkersKoinTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the change worker runs a check from Koin`() = runTest {
        var checks = 0
        val context = startWorkerGraph(
            module {
                // Signed out: the check skips before it reads anything else.
                factory {
                    checks++
                    ScheduleChangesCheck(
                        sessionTokens = FakeSessionTokenStore(signedIn = false),
                        scheduleChecks = PreferenceStores().scheduleChecks,
                        repository = unusedPort(),
                        notifier = unusedPort(),
                        timeProvider = FixedAcademicTime(),
                    )
                }
            }
        )

        assertEquals(Result.success(), buildWorker<ScheduleChangesWorker>(context).doWork())
        assertEquals(1, checks)
    }

    @Test
    fun `the calendar worker runs Koin's calendar sync`() = runTest {
        var syncs = 0
        val context = startWorkerGraph(
            module {
                single {
                    syncs++
                    DefaultCalendarSync(
                        repository = unusedPort(),
                        scheduler = unusedPort(),
                        sessionTokens = FakeSessionTokenStore(signedIn = false),
                        demo = FakeDemoMode(),
                    )
                }
            }
        )

        assertEquals(Result.success(), buildWorker<CalendarSyncWorker>(context).doWork())
        assertEquals(1, syncs)
    }

    @Test
    fun `the widget worker without widgets stops its work before it reads the graph`() = runTest {
        // Nothing to draw: the worker cancels its own work and resolves none of its dependencies.
        val context = startWorkerGraph(module { })
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        assertEquals(Result.success(), buildWorker<ScheduleWidgetUpdateWorker>(context).doWork())
    }
}
