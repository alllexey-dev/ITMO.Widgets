@file:OptIn(ExperimentalForeignApi::class)

package dev.alllexey.itmowidgets.core.work

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSince1970

/** Hands one app refresh request to the system; [BGTaskScheduler] in the app, a recording fake in tests. */
fun interface AppRefreshSubmitter {
    /** Replaces the pending request of [identifier] with one that may start from [earliestEpochSeconds]. */
    fun submit(identifier: String, earliestEpochSeconds: Double)
}

/** The system's scheduler. It is read at the call, never at construction: a test binary has no app to schedule. */
object SystemAppRefreshSubmitter : AppRefreshSubmitter {
    override fun submit(identifier: String, earliestEpochSeconds: Double) {
        val request = BGAppRefreshTaskRequest(identifier)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSince1970(earliestEpochSeconds)
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            check(BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error.ptr)) {
                "BGTaskScheduler refused $identifier: ${error.value?.localizedDescription}"
            }
        }
    }
}

/**
 * The iOS [CheckScheduler] of every background check (IO-14). iOS has one app refresh task for the app,
 * [TASK_IDENTIFIER], which runs the background runner of `shared/ios`; each check keeps its own period inside that
 * runner. So [ensurePeriodic] only asks the system for the next wake, [runOnce] asks the running app for a run now,
 * and [cancel] does nothing: the task also refreshes the widgets, and the runner skips a check whose switch is off.
 *
 * The system decides when the task runs: it learns from how often the app is used, never runs it while Background
 * App Refresh is off or in Low Power Mode, and may delay it by hours. Nothing here promises a time.
 */
class AppRefreshScheduler(
    private val submitter: AppRefreshSubmitter,
    private val clock: Clock,
    private val log: AppLog,
) : CheckScheduler {

    private val requests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A run the app asked for ([runOnce]); the background runner collects it while the app runs. */
    val runRequests: Flow<Unit> = requests.asSharedFlow()

    /** Asks for the next wake no earlier than [EARLIEST_WAKE] from now; repeating it replaces the pending request. */
    override fun ensurePeriodic() {
        val earliest = clock.now() + EARLIEST_WAKE
        try {
            submitter.submit(TASK_IDENTIFIER, earliest.toEpochMilliseconds() / MILLIS_PER_SECOND)
        } catch (error: Exception) {
            log.warn(TAG, "Could not schedule the app refresh", error)
        }
    }

    override fun runOnce() {
        requests.tryEmit(Unit)
    }

    override fun cancel() = Unit

    companion object {
        /**
         * The app refresh task, a stable identifier: `BGTaskSchedulerPermittedIdentifiers` in the app's Info.plist
         * lists it, and SwiftUI's `.backgroundTask(.appRefresh(_:))` handles it (`StableIdentifiersTests`).
         */
        const val TASK_IDENTIFIER = "dev.alllexey.itmowidgets.refresh"

        /** The shortest wait the app asks for; the widget timelines reach to the end of tomorrow anyway. */
        val EARLIEST_WAKE: Duration = 1.hours

        private const val MILLIS_PER_SECOND = 1000.0
        private const val TAG = "AppRefresh"
    }
}
