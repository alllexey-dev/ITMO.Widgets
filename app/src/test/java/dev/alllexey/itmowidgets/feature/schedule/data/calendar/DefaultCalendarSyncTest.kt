package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.feature.schedule.FakeCalendarSyncRepository
import dev.alllexey.itmowidgets.feature.schedule.FakeCalendarSyncScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCalendarSyncTest {

    private val tokens = Tokens()
    private val scheduler = FakeCalendarSyncScheduler()
    private val repository = FakeCalendarSyncRepository()
    private val sync = DefaultCalendarSync(repository, scheduler, tokens)

    @Test
    fun `turning on starts the periodic work and syncs at once`() = runTest {
        assertEquals(CalendarSyncResult.DONE, sync.enable())

        assertEquals(1, repository.enables)
        assertEquals(1, scheduler.ensureCalls)
        assertEquals(1, scheduler.runOnceCalls)
    }

    @Test
    fun `a refused turn on schedules nothing`() = runTest {
        repository.enableResult = CalendarSyncResult.NO_PERMISSION

        assertEquals(CalendarSyncResult.NO_PERMISSION, sync.enable())

        assertEquals(0, scheduler.ensureCalls)
        assertEquals(0, scheduler.runOnceCalls)
    }

    @Test
    fun `turning off stops the work and removes the events`() = runTest {
        sync.disable()

        assertEquals(0, scheduler.ensureCalls)
        assertEquals(1, repository.disables)
        assertTrue(scheduler.cancelCalls >= 1)
    }

    @Test
    fun `a calendar still to sweep keeps the work after turning off and runs it`() = runTest {
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
    fun `the work follows both the session and the switch`() = runTest {
        for ((signedIn, enabled) in listOf(true to true, true to false, false to true, false to false)) {
            val scheduler = FakeCalendarSyncScheduler()
            val sync = DefaultCalendarSync(FakeCalendarSyncRepository(enabled), scheduler, Tokens(signedIn))

            sync.syncWork()
            sync.requestSync()

            val running = signedIn && enabled
            assertEquals("$signedIn/$enabled", if (running) 1 else 0, scheduler.ensureCalls)
            assertEquals("$signedIn/$enabled", if (running) 0 else 1, scheduler.cancelCalls)
            assertEquals("$signedIn/$enabled", if (running) 1 else 0, scheduler.runOnceCalls)
        }
    }

    @Test
    fun `a run is skipped without a session or with the switch off`() = runTest {
        assertEquals(CheckOutcome.SKIPPED, sync.run())
        repository.state.value = CalendarSyncState(enabled = true)
        tokens.refresh = false
        assertEquals(CheckOutcome.SKIPPED, sync.run())
        assertEquals(0, repository.syncs)
    }

    @Test
    fun `a failed run asks for a retry and a signed-out one does not`() = runTest {
        repository.state.value = CalendarSyncState(enabled = true)

        assertEquals(CheckOutcome.DONE, sync.run())
        repository.syncResult = AppResult.Failure(AppError.Network)
        assertEquals(CheckOutcome.RETRY, sync.run())
        repository.syncResult = AppResult.Failure(AppError.Unauthorized)
        assertEquals(CheckOutcome.DONE, sync.run())
        assertEquals(0, scheduler.cancelCalls)
    }

    @Test
    fun `a run that turned sync off cancels the work`() = runTest {
        repository.state.value = CalendarSyncState(enabled = true)
        repository.onSync = { repository.state.value = CalendarSyncState(problem = CalendarSyncProblem.NO_PERMISSION) }

        assertEquals(CheckOutcome.DONE, sync.run())

        assertEquals(1, scheduler.cancelCalls)
    }

    @Test
    fun `stop cancels`() {
        sync.stopWork()

        assertEquals(1, scheduler.cancelCalls)
    }

    private class Tokens(var refresh: Boolean = true) : SessionTokenStore {
        override fun hasRefreshToken() = refresh
        override fun getIdToken(): String? = null
        override fun replaceWithRefreshToken(refreshToken: String) = Unit
        override fun replaceWithTokens(tokens: SessionTokens) = Unit
        override fun clearTokens() = Unit
    }
}
