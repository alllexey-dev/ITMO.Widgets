package dev.alllexey.itmowidgets.ios.background

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.RefreshStep
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.core.work.RefreshStepLog
import dev.alllexey.itmowidgets.testkit.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The runner with fake steps: order, the deadline, the period skip, retries and runs that never overlap. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundRunnerTest {

    private val clock = FakeClock(Instant.parse("2026-10-07T09:00:00Z"))
    private val stepLog = InMemoryStepLog()
    private val events = mutableListOf<String>()

    @Test
    fun stepsRunInOrderThenTheNotificationsSettleThenTheNextWakeIsAsked() = runTest {
        val runner = runner(step("widgets"), step("schedule", period = 2.hours), step("marks", period = 2.hours))

        val report = runner.run()

        assertEquals(listOf("widgets", "schedule", "marks", "settle", "reschedule"), events)
        assertEquals(listOf("widgets", "schedule", "marks"), report.steps.map { it.key })
        assertTrue(report.steps.all { it.result == StepResult.DONE })
    }

    @Test
    fun theDeadlineCancelsTheRunningStepAndLeavesTheRestOut() = runTest {
        var cancelled = false
        val slow = RefreshStep("schedule", 2.hours) {
            events += "schedule"
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val runner = runner(step("widgets"), slow, step("marks", period = 2.hours))

        val report = runner.run()

        assertEquals(StepResult.DONE, report.resultOf("widgets"))
        assertEquals(StepResult.CUT, report.resultOf("schedule"))
        assertEquals(StepResult.CUT, report.resultOf("marks"))
        assertTrue(cancelled)
        assertEquals(listOf("widgets", "schedule", "reschedule"), events)
        assertEquals(BackgroundRunner.DEADLINE, testScheduler.currentTime.milliseconds)
        // A cut step is not recorded: the next run tries it at once.
        assertNull(stepLog.dueAt("schedule"))
    }

    @Test
    fun aStepThatRanWithinItsPeriodIsSkippedOnAnEarlyWakeOrAForeground() = runTest {
        val runner = runner(step("widgets"), step("schedule", period = 2.hours))

        runner.run()
        clock.advanceBy(1.hours)
        val early = runner.run()
        clock.advanceBy(1.hours)
        val due = runner.run()

        assertEquals(StepResult.NOT_DUE, early.resultOf("schedule"))
        assertEquals(StepResult.DONE, due.resultOf("schedule"))
        assertEquals(2, events.count { it == "schedule" })
        assertEquals(3, events.count { it == "widgets" }, "a step without a period runs every time")
    }

    @Test
    fun runsNeverOverlapAndTheSecondFindsTheCheckDone() = runTest {
        var running = 0
        var overlapped = false
        val check = RefreshStep("schedule", 2.hours) {
            running++
            overlapped = overlapped || running > 1
            delay(5.seconds)
            running--
            events += "schedule"
            CheckOutcome.DONE
        }
        val runner = runner(check)

        val first = async { runner.run() }
        val second = async { runner.run() }

        assertEquals(StepResult.DONE, first.await().resultOf("schedule"))
        assertEquals(StepResult.NOT_DUE, second.await().resultOf("schedule"))
        assertEquals(listOf("schedule"), events.filter { it == "schedule" })
        assertTrue(!overlapped)
    }

    @Test
    fun aRetryComesBackAfterTheBackoffTwiceThenWaitsForTheNextPeriod() = runTest {
        val runner = runner(step("schedule", period = 2.hours, outcome = CheckOutcome.RETRY))

        assertEquals(StepResult.RETRY, runner.run().resultOf("schedule"))
        clock.advanceBy(10.minutes)
        assertEquals(StepResult.NOT_DUE, runner.run().resultOf("schedule"))
        clock.advanceBy(5.minutes)
        assertEquals(StepResult.RETRY, runner.run().resultOf("schedule"))
        clock.advanceBy(BackgroundRunner.RETRY_DELAY)
        assertEquals(StepResult.RETRY, runner.run().resultOf("schedule"))
        clock.advanceBy(BackgroundRunner.RETRY_DELAY)
        assertEquals(StepResult.NOT_DUE, runner.run().resultOf("schedule"), "two retries, then the next period")
        clock.advanceBy(2.hours)
        assertEquals(StepResult.RETRY, runner.run().resultOf("schedule"))
        assertEquals(4, events.count { it == "schedule" })
    }

    @Test
    fun aFailingStepCountsAsARetryAndTheNextStepStillRuns() = runTest {
        val failing = RefreshStep("schedule", 2.hours) { error("synthetic failure") }
        val log = RecordingLog()
        val runner = runner(failing, step("marks", period = 2.hours), log = log)

        val report = runner.run()

        assertEquals(StepResult.RETRY, report.resultOf("schedule"))
        assertEquals(StepResult.DONE, report.resultOf("marks"))
        assertEquals(clock.now() + BackgroundRunner.RETRY_DELAY, stepLog.dueAt("schedule"))
        assertTrue(log.warnings.any { "schedule" in it })
    }

    @Test
    fun aSkippedStepIsTriedAgainOnTheNextRun() = runTest {
        val runner = runner(step("schedule", period = 2.hours, outcome = CheckOutcome.SKIPPED))

        assertEquals(StepResult.SKIPPED, runner.run().resultOf("schedule"))
        assertEquals(StepResult.SKIPPED, runner.run().resultOf("schedule"))
        assertEquals(2, events.count { it == "schedule" })
    }

    @Test
    fun theSystemEndingTheTaskStillAsksForTheNextWake() = runTest {
        val runner = runner(RefreshStep("schedule", 2.hours) { awaitCancellation() })

        val run = launch { runner.run() }
        runCurrent()
        run.cancel(CancellationException("the app refresh task expired"))
        advanceUntilIdle()

        assertEquals(listOf("reschedule"), events)
    }

    @Test
    fun theRunnerRunsOnEveryTrigger() = runTest {
        val runner = runner(step("widgets"))
        val triggers = MutableSharedFlow<Unit>()

        val job = runner.launchIn(backgroundScope, triggers)
        runCurrent()
        triggers.emit(Unit)
        triggers.emit(Unit)
        advanceUntilIdle()
        job.cancel()

        assertEquals(2, events.count { it == "widgets" })
    }

    @Test
    fun theBoundStepsRunInTheFixedOrderAndAnUnplacedStepIsAWiringError() {
        val bound = listOf(
            step(RefreshStepKeys.CALENDAR_SYNC),
            step(RefreshStepKeys.MARKS),
            step(RefreshStepKeys.SCHEDULE_CHANGES),
            step(RefreshStepKeys.WIDGET_SNAPSHOTS),
        )

        assertEquals(RefreshStepKeys.ORDER, IosBackgroundRefresh.ordered(bound).map { it.key })
        assertFailsWith<IllegalArgumentException> { IosBackgroundRefresh.ordered(bound + step("unplaced")) }
    }

    @Test
    fun theMarkCheckRunsAfterTheScheduleChangesWithinTheDeadline() = runTest {
        val schedule = slowStep(RefreshStepKeys.SCHEDULE_CHANGES, RefreshStepKeys.SCHEDULE_CHANGES_PERIOD, 10.seconds)
        val marks = slowStep(RefreshStepKeys.MARKS, RefreshStepKeys.MARKS_PERIOD, 10.seconds)
        val runner = runner(*IosBackgroundRefresh.ordered(listOf(marks, schedule)).toTypedArray())

        val report = runner.run()

        assertEquals(listOf(RefreshStepKeys.SCHEDULE_CHANGES, RefreshStepKeys.MARKS, "settle", "reschedule"), events)
        assertEquals(StepResult.DONE, report.resultOf(RefreshStepKeys.MARKS))
        assertTrue(testScheduler.currentTime.milliseconds < BackgroundRunner.DEADLINE)
    }

    @Test
    fun aSlowScheduleCheckLeavesTheMarkCheckToTheNextRun() = runTest {
        val schedule = slowStep(RefreshStepKeys.SCHEDULE_CHANGES, RefreshStepKeys.SCHEDULE_CHANGES_PERIOD, 20.seconds)
        val marks = slowStep(RefreshStepKeys.MARKS, RefreshStepKeys.MARKS_PERIOD, 10.seconds)
        val runner = runner(schedule, marks)

        val cut = runner.run()
        val next = runner.run()

        assertEquals(StepResult.DONE, cut.resultOf(RefreshStepKeys.SCHEDULE_CHANGES))
        assertEquals(StepResult.CUT, cut.resultOf(RefreshStepKeys.MARKS))
        assertEquals(StepResult.NOT_DUE, next.resultOf(RefreshStepKeys.SCHEDULE_CHANGES))
        assertEquals(StepResult.DONE, next.resultOf(RefreshStepKeys.MARKS), "a cut check runs on the next wake")
    }

    @Test
    fun theMarkCheckKeepsAndroidsThreeHoursAcrossWakesAndForegrounds() = runTest {
        val runner = runner(step(RefreshStepKeys.MARKS, period = RefreshStepKeys.MARKS_PERIOD))

        runner.run()
        clock.advanceBy(2.hours)
        val foreground = runner.run()
        clock.advanceBy(1.hours)
        val due = runner.run()

        assertEquals(StepResult.NOT_DUE, foreground.resultOf(RefreshStepKeys.MARKS))
        assertEquals(StepResult.DONE, due.resultOf(RefreshStepKeys.MARKS))
        assertEquals(2, events.count { it == RefreshStepKeys.MARKS })
        assertEquals(3.hours, RefreshStepKeys.MARKS_PERIOD)
    }

    private fun runner(vararg steps: RefreshStep, log: AppLog = RecordingLog()) = BackgroundRunner(
        steps = steps.toList(),
        stepLog = stepLog,
        reschedule = { events += "reschedule" },
        settle = { events += "settle" },
        clock = clock,
        log = log,
    )

    private fun step(key: String, period: Duration = Duration.ZERO, outcome: CheckOutcome = CheckOutcome.DONE) =
        RefreshStep(key, period) {
            events += key
            outcome
        }

    /** A check of [key] that takes [duration] of the runner's time. */
    private fun slowStep(key: String, period: Duration, duration: Duration) = RefreshStep(key, period) {
        delay(duration)
        events += key
        CheckOutcome.DONE
    }

    private class InMemoryStepLog : RefreshStepLog {
        private val due = mutableMapOf<String, Pair<Instant, Int>>()

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

    private class RecordingLog : AppLog {
        val warnings = mutableListOf<String>()

        override fun info(tag: String, message: String) = Unit

        override fun warn(tag: String, message: String, error: Throwable?) {
            warnings += message
        }

        override fun error(tag: String, message: String, error: Throwable?) = Unit
    }
}
