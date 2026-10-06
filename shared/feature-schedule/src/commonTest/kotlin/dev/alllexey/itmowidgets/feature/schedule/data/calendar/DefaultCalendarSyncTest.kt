package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.feature.schedule.FakeCalendarSyncRepository
import dev.alllexey.itmowidgets.feature.schedule.FakeCalendarSyncScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DefaultCalendarSyncTest {

    private val tokens = FakeSessionTokenStore()
    private val scheduler = FakeCalendarSyncScheduler()
    private val repository = FakeCalendarSyncRepository()
    private val sync = DefaultCalendarSync(repository, scheduler, tokens, noDemo())

    @Test
    fun turningOnStartsThePeriodicWorkAndSyncsAtOnce() = runTest {
        assertEquals(CalendarSyncResult.DONE, sync.enable())

        assertEquals(1, repository.enables)
        assertEquals(1, scheduler.ensureCalls)
        assertEquals(1, scheduler.runOnceCalls)
    }

    @Test
    fun theDemoCannotTurnSynchronizationOn() = runTest {
        val demo = DefaultCalendarSync(repository, scheduler, tokens, FakeDemoMode(active = true))

        assertEquals(CalendarSyncResult.DEMO_UNAVAILABLE, demo.enable())

        assertEquals(0, repository.enables)
        assertEquals(0, scheduler.ensureCalls)
    }

    @Test
    fun aRefusedTurnOnSchedulesNothing() = runTest {
        repository.enableResult = CalendarSyncResult.NO_PERMISSION

        assertEquals(CalendarSyncResult.NO_PERMISSION, sync.enable())

        assertEquals(0, scheduler.ensureCalls)
        assertEquals(0, scheduler.runOnceCalls)
    }

    @Test
    fun turningOffStopsTheWorkAndRemovesTheEvents() = runTest {
        sync.disable()

        assertEquals(0, scheduler.ensureCalls)
        assertEquals(1, repository.disables)
        assertTrue(scheduler.cancelCalls >= 1)
    }

    @Test
    fun aCalendarStillToSweepKeepsTheWorkAfterTurningOffAndRunsIt() = runTest {
        repository.pendingCleanup = true

        sync.disable()
        assertEquals(1, scheduler.ensureCalls)
        assertEquals(CheckOutcome.DONE, sync.run())
        assertEquals(1, repository.syncs)

        repository.onSync = { repository.pendingCleanup = false }
        sync.run()
        assertEquals(CheckOutcome.SKIPPED, sync.run())
        assertEquals(2, repository.syncs)
    }

    @Test
    fun theWorkFollowsBothTheSessionAndTheSwitch() = runTest {
        for ((signedIn, enabled) in listOf(true to true, true to false, false to true, false to false)) {
            val scheduler = FakeCalendarSyncScheduler()
            val sync = DefaultCalendarSync(FakeCalendarSyncRepository(enabled), scheduler, FakeSessionTokenStore(signedIn), noDemo())

            sync.syncWork()
            sync.requestSync()

            val running = signedIn && enabled
            assertEquals(if (running) 1 else 0, scheduler.ensureCalls, "$signedIn/$enabled")
            assertEquals(if (running) 0 else 1, scheduler.cancelCalls, "$signedIn/$enabled")
            assertEquals(if (running) 1 else 0, scheduler.runOnceCalls, "$signedIn/$enabled")
        }
    }

    @Test
    fun aRunIsSkippedWithoutASessionOrWithTheSwitchOff() = runTest {
        assertEquals(CheckOutcome.SKIPPED, sync.run())
        repository.state.value = CalendarSyncState(enabled = true)
        tokens.signedIn = false
        assertEquals(CheckOutcome.SKIPPED, sync.run())
        assertEquals(0, repository.syncs)
    }

    @Test
    fun aFailedRunAsksForARetryAndASignedOutOneDoesNot() = runTest {
        repository.state.value = CalendarSyncState(enabled = true)

        assertEquals(CheckOutcome.DONE, sync.run())
        repository.syncResult = AppResult.Failure(AppError.Network)
        assertEquals(CheckOutcome.RETRY, sync.run())
        repository.syncResult = AppResult.Failure(AppError.Unauthorized)
        assertEquals(CheckOutcome.DONE, sync.run())
        assertEquals(0, scheduler.cancelCalls)
    }

    @Test
    fun aRunThatTurnedSyncOffCancelsTheWork() = runTest {
        repository.state.value = CalendarSyncState(enabled = true)
        repository.onSync = { repository.state.value = CalendarSyncState(problem = CalendarSyncProblem.NO_PERMISSION) }

        assertEquals(CheckOutcome.DONE, sync.run())

        assertEquals(1, scheduler.cancelCalls)
    }

    @Test
    fun stopCancels() {
        sync.stopWork()

        assertEquals(1, scheduler.cancelCalls)
    }
}
