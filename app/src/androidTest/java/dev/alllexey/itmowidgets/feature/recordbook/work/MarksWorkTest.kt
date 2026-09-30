package dev.alllexey.itmowidgets.feature.recordbook.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.testing.TestUi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's real WorkManager and scheduler of the mark check. A run the test starts takes the app's own path: without
 * a session it is skipped, with the owner's session it is an ordinary check. The test reads no marks.
 */
@RunWith(AndroidJUnit4::class)
class MarksWorkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dependencies = EntryPointAccessors.fromApplication(context, MarksTestEntryPoint::class.java)
    private val scheduler = dependencies.marksScheduler()
    private val workManager = WorkManager.getInstance(context)

    @After
    fun restoreOwnerState() {
        scheduler.cancel()
        // The switches and the session decide again, as on an app start.
        runBlocking { dependencies.marksTracking().syncWork() }
    }

    @Test
    fun periodicCheckIsUniqueAndKeepsItsScheduleWhenRepeated() {
        scheduler.ensurePeriodic()
        val first = pending(PERIODIC).single()
        scheduler.ensurePeriodic()
        val second = pending(PERIODIC).single()

        assertEquals(first.id, second.id)
        assertTrue(second.state.toString(), second.state == WorkInfo.State.ENQUEUED || second.state == WorkInfo.State.RUNNING)
        assertEquals(NetworkType.CONNECTED, second.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(3), second.periodicityInfo!!.repeatIntervalMillis)
        assertTrue(TAG in second.tags)
    }

    @Test
    fun cancelStopsThePeriodicCheck() {
        scheduler.ensurePeriodic()
        scheduler.cancel()

        TestUi.eventually(message = "The periodic check must be cancelled") {
            val infos = infos(PERIODIC)
            assertTrue(infos.isNotEmpty())
            assertTrue(infos.joinToString { it.state.name }, infos.all { it.state == WorkInfo.State.CANCELLED })
        }
    }

    @Test
    fun oneOffCheckReplacesAPendingOne() {
        scheduler.runOnce()
        scheduler.runOnce()

        assertTrue(pending(ONE_OFF).size <= 1)
        val latest = infos(ONE_OFF).single { it.state != WorkInfo.State.CANCELLED }
        assertTrue(TAG in latest.tags)
        assertEquals(NetworkType.CONNECTED, latest.constraints.requiredNetworkType)
        assertTrue(
            latest.state.toString(),
            latest.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED)
        )
    }

    private fun infos(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    private fun pending(name: String): List<WorkInfo> = infos(name).filterNot { it.state.isFinished }

    private companion object {
        const val PERIODIC = "marks-check"
        const val ONE_OFF = "marks-check-now"
        const val TAG = "marks"
    }
}
