package dev.alllexey.itmowidgets.core.work

import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** One app refresh task for every check: the next wake an hour on, a run now through the app, no per-check cancel. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppRefreshSchedulerTest {

    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-07T09:00:00Z")
    }
    private val submitted = mutableListOf<Pair<String, Double>>()
    private val log = RecordingAppLog()

    @Test
    fun ensurePeriodicAsksForTheOneTaskAnHourOn() {
        scheduler { identifier, earliest -> submitted += identifier to earliest }.ensurePeriodic()

        val expected = (clock.now() + AppRefreshScheduler.EARLIEST_WAKE).epochSeconds.toDouble()
        assertEquals(listOf("dev.alllexey.itmowidgets.refresh" to expected), submitted)
    }

    @Test
    fun aRefusedRequestIsLoggedNotThrown() {
        scheduler { _, _ -> error("unavailable on the simulator") }.ensurePeriodic()

        assertEquals("WARN:AppRefresh:Could not schedule the app refresh", log.lines.single())
    }

    @Test
    fun runOnceAsksTheRunningAppAndCancelLeavesTheSharedTask() = runTest {
        val scheduler = scheduler { identifier, earliest -> submitted += identifier to earliest }
        var asked = false
        launch { scheduler.runRequests.first(); asked = true }
        runCurrent()

        scheduler.runOnce()
        scheduler.cancel()
        runCurrent()

        assertTrue(asked)
        assertEquals(emptyList(), submitted)
    }

    private fun scheduler(submitter: AppRefreshSubmitter) = AppRefreshScheduler(submitter, clock, log)
}
