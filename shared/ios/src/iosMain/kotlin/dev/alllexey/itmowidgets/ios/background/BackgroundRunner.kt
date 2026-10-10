package dev.alllexey.itmowidgets.ios.background

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.notification.IosAppNotifier
import dev.alllexey.itmowidgets.core.notification.LocalNotificationCenter
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.work.AppRefreshScheduler
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.MAX_RETRIES
import dev.alllexey.itmowidgets.core.work.RefreshStep
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.core.work.RefreshStepLog
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.IosMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksFixture
import dev.alllexey.itmowidgets.feature.schedule.data.changes.IosScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangeFixture
import dev.alllexey.itmowidgets.ios.di.IosKoin
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/** How one step ended in one run. */
enum class StepResult {
    /** The step ran and finished; its next run is a period away. */
    DONE,

    /** The step ran and asked for a retry, or threw; it runs again after [BackgroundRunner.RETRY_DELAY]. */
    RETRY,

    /** The step had nothing to do (signed out, its switch off); the next run tries it again. */
    SKIPPED,

    /** The step ran within its period and was left out. */
    NOT_DUE,

    /** The deadline ended the run before or while the step ran. */
    CUT,
}

data class StepRun(val key: String, val result: StepResult)

/** What one run did, step by step in the runner's order. */
data class RefreshReport(val steps: List<StepRun>) {
    fun resultOf(key: String): StepResult? = steps.firstOrNull { it.key == key }?.result
}

/**
 * The background refresh of the iOS app (IO-14): [steps] in their order within [deadline], then [settle] (the
 * notifications reach the system), then [reschedule] (the next app refresh task), also when the run was cut or
 * cancelled. The app refresh task (`iosApp/Sources/Background/`), the app's launch, every return to the foreground
 * and `CheckScheduler.runOnce` run the same [run]; runs never overlap, a second one waits for the first.
 *
 * The system gives an app refresh task about 30 s and cancels the Swift task when it expires, which cancels [run];
 * the runner keeps 5 s of it for the rest, so [deadline] is 25 s. A cut step is cancelled, so each step leaves a
 * state the next run can continue from.
 */
class BackgroundRunner(
    private val steps: List<RefreshStep>,
    private val stepLog: RefreshStepLog,
    private val reschedule: () -> Unit,
    private val settle: suspend () -> Unit,
    private val clock: Clock,
    private val log: AppLog,
    private val deadline: Duration = DEADLINE,
) {
    private val mutex = Mutex()

    init {
        require(steps.map { it.key }.distinct().size == steps.size) { "Two refresh steps share a key" }
    }

    suspend fun run(): RefreshReport = mutex.withLock {
        val results = mutableListOf<StepRun>()
        try {
            val finished = withTimeoutOrNull(deadline) {
                steps.forEach { step -> results += StepRun(step.key, runStep(step)) }
                settle()
            }
            if (finished == null) {
                log.warn(TAG, "The refresh hit its ${deadline.inWholeSeconds} s deadline after ${results.size} steps")
                steps.drop(results.size).forEach { results += StepRun(it.key, StepResult.CUT) }
            }
        } finally {
            reschedule()
        }
        RefreshReport(results.toList())
    }

    /** Runs on every value of [triggers] in [scope] until the scope ends; a failed run is logged. */
    fun launchIn(scope: CoroutineScope, triggers: Flow<Unit>): Job = scope.launch {
        triggers.collect {
            try {
                run()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                log.warn(TAG, "The refresh failed", error)
            }
        }
    }

    private suspend fun runStep(step: RefreshStep): StepResult {
        val startedAt = clock.now()
        if (step.period > Duration.ZERO) {
            val due = stepLog.dueAt(step.key)
            if (due != null && startedAt < due) return StepResult.NOT_DUE
        }
        val outcome = try {
            step.action()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.warn(TAG, "The ${step.key} step failed", error)
            CheckOutcome.RETRY
        }
        if (step.period > Duration.ZERO) record(step, outcome, startedAt)
        return when (outcome) {
            CheckOutcome.DONE -> StepResult.DONE
            CheckOutcome.RETRY -> StepResult.RETRY
            CheckOutcome.SKIPPED -> StepResult.SKIPPED
        }
    }

    /** Android's retry policy: [MAX_RETRIES] retries after [RETRY_DELAY], then the next period. */
    private fun record(step: RefreshStep, outcome: CheckOutcome, startedAt: Instant) = when (outcome) {
        CheckOutcome.DONE -> stepLog.record(step.key, startedAt + step.period, retries = 0)
        CheckOutcome.SKIPPED -> stepLog.forget(step.key)
        CheckOutcome.RETRY -> {
            val retries = stepLog.retries(step.key) + 1
            if (retries > MAX_RETRIES) stepLog.record(step.key, startedAt + step.period, retries = 0)
            else stepLog.record(step.key, startedAt + RETRY_DELAY, retries)
        }
    }

    companion object {
        val DEADLINE: Duration = 25.seconds

        /** Android's backoff of the schedule change check (`SCHEDULE_CHANGES_SPEC`). */
        val RETRY_DELAY: Duration = 15.minutes

        private const val TAG = "BackgroundRefresh"
    }
}

/**
 * The app's one [BackgroundRunner] (IO-14), built from the started graph by [start]: the [RefreshStep]s the feature
 * modules bind under their keys, in [RefreshStepKeys.ORDER]; `AppRefreshScheduler` for the next wake; the notifier's
 * posts to settle. It runs at once (the launch), on every return to the foreground and on `runOnce`; the app refresh
 * task calls [run] (`iosApp/Sources/Background/`). Koin modules live in `core.di` and the features' `di` packages, so
 * the runner is assembled here rather than defined in a module.
 */
object IosBackgroundRefresh {

    @Volatile
    private var runner: BackgroundRunner? = null

    /** Builds and starts the runner after `startKoinIos`; a later call keeps the running one and returns false. */
    fun start(): Boolean {
        if (runner != null) return false
        val koin = IosKoin.koin()
        val scheduler = koin.get<AppRefreshScheduler>()
        val notifier = koin.get<IosAppNotifier>()
        val started = BackgroundRunner(
            steps = ordered(koin.getAll<RefreshStep>()),
            stepLog = koin.get(),
            reschedule = scheduler::ensurePeriodic,
            settle = notifier::awaitPosts,
            clock = koin.get(),
            log = koin.get(),
        )
        runner = started
        val triggers = merge(flowOf(Unit), applicationForegrounds(), scheduler.runRequests)
        started.launchIn(CoroutineScope(SupervisorJob() + koin.get<AppDispatchers>().main), triggers)
        return true
    }

    /** One run, the app refresh task's; waits for a run already going. */
    suspend fun run(): RefreshReport = checkNotNull(runner) { "IosBackgroundRefresh.start has not run" }.run()

    /** The bound steps in [RefreshStepKeys.ORDER]; a step without a place there is a wiring error. */
    internal fun ordered(steps: List<RefreshStep>): List<RefreshStep> {
        val unknown = steps.map { it.key } - RefreshStepKeys.ORDER.toSet()
        require(unknown.isEmpty()) { "Refresh steps without a place in RefreshStepKeys.ORDER: $unknown" }
        return steps.sortedBy { RefreshStepKeys.ORDER.indexOf(it.key) }
    }
}

/**
 * Debug builds' `-itmoRunRefresh`: posts the digest of a synthetic change (`ScheduleChangeFixture`) through the
 * schedule change notifier to [center], so the notification path is visible without a real change.
 */
suspend fun postFixtureScheduleChange(center: LocalNotificationCenter) {
    val koin = IosKoin.koin()
    val time = koin.get<AcademicTimeProvider>()
    val notifier = IosAppNotifier(center, koin.get(), koin.get())
    IosScheduleChangeNotifier(notifier).show(ScheduleChangeFixture.digest(time.today(), time.now()))
    notifier.awaitPosts()
}

/** [postFixtureScheduleChange] to the app's notification centre. */
suspend fun postFixtureScheduleChange() = postFixtureScheduleChange(IosKoin.koin().get<LocalNotificationCenter>())

/**
 * Debug builds' `-itmoRunRefresh`: posts a synthetic marks digest (`MarksFixture`) through the marks notifier to
 * [center], so the marks notification is visible without new marks.
 */
suspend fun postFixtureMarkDigest(center: LocalNotificationCenter) {
    val koin = IosKoin.koin()
    val notifier = IosAppNotifier(center, koin.get(), koin.get())
    IosMarksNotifier(notifier).showDigest(MarksFixture.digest, target = null)
    notifier.awaitPosts()
}

/** [postFixtureMarkDigest] to the app's notification centre. */
suspend fun postFixtureMarkDigest() = postFixtureMarkDigest(IosKoin.koin().get<LocalNotificationCenter>())

/**
 * Debug builds' `-itmoNotificationFixture bars-login`: posts the "sign in to BARS" reminder through the marks notifier
 * to the app's notification centre, so a UI test taps it without an expired BARS session.
 */
suspend fun postFixtureBarsPrompt() {
    val koin = IosKoin.koin()
    val notifier = IosAppNotifier(koin.get<LocalNotificationCenter>(), koin.get(), koin.get())
    IosMarksNotifier(notifier).showBarsPrompt()
    notifier.awaitPosts()
}

/** Every return of the app from the background. */
private fun applicationForegrounds(): Flow<Unit> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    val observer = center.addObserverForName(
        UIApplicationWillEnterForegroundNotification,
        null,
        NSOperationQueue.mainQueue,
    ) { trySend(Unit) }
    awaitClose { center.removeObserver(observer) }
}
