package dev.alllexey.itmowidgets.core.work

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import platform.Foundation.NSUserDefaults

/**
 * One step of the background refresh. [action] is idempotent: the runner may call it from the app refresh task and
 * from every return to the foreground. A step with a [period] runs at most once per period, as its Android worker
 * does, so My ITMO sees no more calls from an iPhone than from an Android phone; [Duration.ZERO] runs every time.
 */
class RefreshStep(
    val key: String,
    val period: Duration,
    val action: suspend () -> CheckOutcome,
)

/**
 * The steps of the iOS background runner (`shared/ios`) by key, in the order they run: widget snapshots
 * first (the schedule timeline, then the sport widget), then the checks (schedule changes, marks with the BARS renewal, the calendar sync). Each later check adds
 * one line here and binds its [RefreshStep] under its key in its feature's iOS module.
 */
object RefreshStepKeys {
    const val WIDGET_SNAPSHOTS = "widget-snapshots"
    const val SPORT_WIDGET = "sport-widget"
    const val SCHEDULE_CHANGES = "schedule-changes"
    const val MARKS = "marks"
    const val CALENDAR_SYNC = "calendar-sync"

    val ORDER: List<String> = listOf(
        WIDGET_SNAPSHOTS,
        SPORT_WIDGET,
        SCHEDULE_CHANGES,
        MARKS,
        CALENDAR_SYNC,
    )

    /**
     * The sport widget asks My ITMO for the score and the bookings; a booking made in the app writes at once, so
     * the runner refreshes it at most this often.
     */
    val SPORT_WIDGET_PERIOD: Duration = 30.minutes

    /** Android's `SCHEDULE_CHANGES_SPEC.period`. */
    val SCHEDULE_CHANGES_PERIOD: Duration = 2.hours

    /** Android's `MARKS_SPEC.period`. */
    val MARKS_PERIOD: Duration = 3.hours

    /** Android's `CALENDAR_SYNC_SPEC.period`. */
    val CALENDAR_SYNC_PERIOD: Duration = 2.hours
}

/** When each step of the runner is next due, kept across launches; never a token or user data. */
interface RefreshStepLog {
    fun dueAt(key: String): Instant?
    fun retries(key: String): Int
    fun record(key: String, dueAt: Instant, retries: Int)
    fun forget(key: String)
    fun forgetAll()
}

/**
 * [RefreshStepLog] in the app's own `NSUserDefaults` (the app process only; extensions never read it). Cleared on
 * sign-out, so the next account's first check is not held back by the previous one's period.
 */
class UserDefaultsRefreshStepLog(private val defaults: NSUserDefaults) : RefreshStepLog, SessionDataCleaner {

    override fun dueAt(key: String): Instant? = defaults.objectForKey(dueKey(key))?.let {
        Instant.fromEpochMilliseconds(defaults.doubleForKey(dueKey(key)).toLong())
    }

    override fun retries(key: String): Int = defaults.integerForKey(retriesKey(key)).toInt()

    override fun record(key: String, dueAt: Instant, retries: Int) {
        defaults.setDouble(dueAt.toEpochMilliseconds().toDouble(), dueKey(key))
        defaults.setInteger(retries.toLong(), retriesKey(key))
        keys(defaults.stringArrayForKey(KEYS).orEmpty() + key)
    }

    override fun forget(key: String) {
        defaults.removeObjectForKey(dueKey(key))
        defaults.removeObjectForKey(retriesKey(key))
    }

    override fun forgetAll() {
        defaults.stringArrayForKey(KEYS).orEmpty().forEach { forget(it.toString()) }
        defaults.removeObjectForKey(KEYS)
    }

    override suspend fun clearSessionData() = forgetAll()

    private fun keys(keys: List<Any?>) = defaults.setObject(keys.map { it.toString() }.distinct(), KEYS)

    private fun dueKey(key: String) = "$PREFIX.$key.dueAt"

    private fun retriesKey(key: String) = "$PREFIX.$key.retries"

    private companion object {
        const val PREFIX = "backgroundRefresh"
        const val KEYS = "$PREFIX.keys"
    }
}
