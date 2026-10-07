package dev.alllexey.itmowidgets.core.work

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults

/** The step log in a defaults suite of the test's own: due times and retries survive, sign-out forgets them. */
class UserDefaultsRefreshStepLogTest {

    private val suite = "itmo-refresh-test-${Random.nextLong().toULong()}"
    private val defaults = NSUserDefaults(suiteName = suite)

    @AfterTest
    fun removeSuite() = defaults.removePersistentDomainForName(suite)

    @Test
    fun aRecordReadsBackUntilTheSessionIsCleared() = runTest {
        val log = UserDefaultsRefreshStepLog(defaults)
        val due = Instant.parse("2026-10-07T11:00:00Z")

        assertNull(log.dueAt("schedule-changes"))
        log.record("schedule-changes", due, retries = 1)
        log.record("marks", due, retries = 0)

        val reread = UserDefaultsRefreshStepLog(defaults)
        assertEquals(due, reread.dueAt("schedule-changes"))
        assertEquals(1, reread.retries("schedule-changes"))

        reread.forget("marks")
        assertNull(reread.dueAt("marks"))
        reread.clearSessionData()
        assertNull(reread.dueAt("schedule-changes"))
        assertEquals(0, reread.retries("schedule-changes"))
    }
}
