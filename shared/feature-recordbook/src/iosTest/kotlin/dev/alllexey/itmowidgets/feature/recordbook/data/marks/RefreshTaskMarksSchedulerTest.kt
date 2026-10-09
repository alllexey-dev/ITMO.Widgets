package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.core.work.RefreshStepLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The mark check's scheduler on iOS: one app refresh task for every check, the mark check one step of it. */
class RefreshTaskMarksSchedulerTest {

    private val refresh = RecordingRefresh()
    private val stepLog = MapStepLog()
    private val scheduler = RefreshTaskMarksScheduler(refresh, stepLog)

    @Test
    fun runningOnceMakesTheStepDueAndAsksForARun() {
        stepLog.record(RefreshStepKeys.MARKS, DUE, retries = 1)
        stepLog.record(RefreshStepKeys.SCHEDULE_CHANGES, DUE, retries = 0)

        scheduler.runOnce()

        assertEquals(setOf(RefreshStepKeys.SCHEDULE_CHANGES), stepLog.due.keys, "only the mark check is due at once")
        assertEquals(listOf("runOnce"), refresh.calls)
    }

    @Test
    fun theTaskStaysForTheOtherSteps() {
        scheduler.ensurePeriodic()
        scheduler.cancel()

        assertEquals(listOf("ensurePeriodic"), refresh.calls)
    }

    private class RecordingRefresh : CheckScheduler {
        val calls = mutableListOf<String>()

        override fun ensurePeriodic() {
            calls += "ensurePeriodic"
        }

        override fun runOnce() {
            calls += "runOnce"
        }

        override fun cancel() {
            calls += "cancel"
        }
    }

    private class MapStepLog : RefreshStepLog {
        val due = mutableMapOf<String, Pair<Instant, Int>>()

        override fun dueAt(key: String): Instant? = due[key]?.first

        override fun retries(key: String): Int = due[key]?.second ?: 0

        override fun record(key: String, dueAt: Instant, retries: Int) {
            due[key] = dueAt to retries
        }

        override fun forget(key: String) {
            due.remove(key)
        }

        override fun forgetAll() = due.clear()
    }

    private companion object {
        val DUE: Instant = Instant.parse("2026-10-08T12:00:00Z")
    }
}
