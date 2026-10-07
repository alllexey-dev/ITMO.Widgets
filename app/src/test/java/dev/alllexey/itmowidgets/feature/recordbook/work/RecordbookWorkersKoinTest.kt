package dev.alllexey.itmowidgets.feature.recordbook.work

import android.app.Application
import androidx.work.ListenableWorker.Result
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import dev.alllexey.itmowidgets.core.work.buildWorker
import dev.alllexey.itmowidgets.core.work.startWorkerGraph
import dev.alllexey.itmowidgets.core.work.unusedPort
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksCheck
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The recordbook's workers read their dependencies from Koin (KM-12a), built as WorkManager builds them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RecordbookWorkersKoinTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the marks worker runs a check from Koin`() = runTest {
        var checks = 0
        val context = startWorkerGraph(
            module {
                // Signed out: the check skips before it reads anything else.
                factory {
                    checks++
                    MarksCheck(
                        sessionTokens = FakeSessionTokenStore(signedIn = false),
                        markSources = PreferenceStores().markSources,
                        repository = unusedPort(),
                        notifier = unusedPort(),
                        barsPreference = unusedPort(),
                        timeProvider = FixedAcademicTime(),
                    )
                }
            }
        )

        assertEquals(Result.success(), buildWorker<MarksWorker>(context).doWork())
        assertEquals(1, checks)
    }

    @Test
    fun `the cookie probe logs the failure to its Koin log and never fails the work`() = runTest {
        val log = RecordingAppLog()
        val context = startWorkerGraph(
            module {
                single<AppLog> { log }
                single<BarsLogin> { error("no BARS sign-in in this test") }
            }
        )

        assertEquals(Result.success(), buildWorker<BarsCookieProbeWorker>(context).doWork())
        val line = log.lines.single()
        assertTrue(line, line.startsWith("INFO:BarsCookieProbe:outcome=ERROR step=start"))
    }
}
