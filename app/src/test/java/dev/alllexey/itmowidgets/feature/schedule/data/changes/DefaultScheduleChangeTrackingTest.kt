package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultScheduleChangeTrackingTest {

    private val tokens = Tokens()
    private val settings = AppSettingsStorage(InMemoryPreferencesDataStore())
    private val scheduler = FakeScheduleChangesScheduler()
    private val repository = FakeScheduleChangesRepository()
    private val tracking = DefaultScheduleChangeTracking(settings, tokens, scheduler, repository)

    @Test
    fun `the switch is on by default`() = runTest {
        assertTrue(tracking.observeEnabled().first())
    }

    @Test
    fun `turning the switch off stops the check and forgets the snapshot`() = runTest {
        tracking.setEnabled(false)

        assertFalse(tracking.observeEnabled().first())
        assertEquals(1, scheduler.cancelCalls)
        assertEquals(1, repository.resets)
        assertEquals(0, scheduler.ensureCalls)
    }

    @Test
    fun `turning the switch on starts the check only in a session`() = runTest {
        tracking.setEnabled(true)
        assertEquals(1, scheduler.ensureCalls)
        assertEquals(0, scheduler.cancelCalls)

        tokens.refresh = false
        tracking.setEnabled(true)
        assertEquals(1, scheduler.ensureCalls)
        assertEquals(1, scheduler.cancelCalls)
        assertEquals(0, repository.resets)
    }

    @Test
    fun `sync follows both the session and the switch`() = runTest {
        for ((signedIn, enabled) in listOf(true to true, true to false, false to true, false to false)) {
            val scheduler = FakeScheduleChangesScheduler()
            val settings = AppSettingsStorage(InMemoryPreferencesDataStore()).apply { setScheduleChangesEnabled(enabled) }
            val tracking = DefaultScheduleChangeTracking(settings, Tokens(signedIn), scheduler, FakeScheduleChangesRepository())

            tracking.syncWork()

            val running = signedIn && enabled
            assertEquals("$signedIn/$enabled", if (running) 1 else 0, scheduler.ensureCalls)
            assertEquals("$signedIn/$enabled", if (running) 0 else 1, scheduler.cancelCalls)
        }
    }

    @Test
    fun `stop cancels and check now runs once`() {
        tracking.stopWork()
        tracking.checkNow()

        assertEquals(1, scheduler.cancelCalls)
        assertEquals(1, scheduler.runOnceCalls)
        assertEquals(0, scheduler.ensureCalls)
    }

    private class Tokens(var refresh: Boolean = true) : SessionTokenStore {
        override fun hasRefreshToken() = refresh
        override fun getIdToken(): String? = null
        override fun replaceWithRefreshToken(refreshToken: String) = Unit
        override fun replaceWithTokens(tokens: SessionTokens) = Unit
        override fun clearTokens() = Unit
    }
}
