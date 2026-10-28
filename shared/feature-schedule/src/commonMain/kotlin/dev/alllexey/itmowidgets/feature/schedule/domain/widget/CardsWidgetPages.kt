package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The cards widget's pages: the day the day widget shows (today, or tomorrow once today is over), then each day after
 * it through the sixth after today ([ScheduleWidgetSnapshot.week]). A snapshot without the week has only the first.
 */
fun ScheduleWidgetSnapshot.cardsPages(): List<DayWidgetContent> =
    if (day.tomorrow && week.isNotEmpty()) week else listOf(day) + week

/** How many days after today the first of [cardsPages] is: 1 once today is over and the widget shows tomorrow. */
fun ScheduleWidgetSnapshot.cardsFirstOffset(): Int = if (day.tomorrow && week.isNotEmpty()) 1 else 0

/**
 * A placed cards widget turned to [page] at [at] by its arrows. It returns to the first page [RESET_AFTER] later and
 * at the next midnight, so a glance at the home screen shows the current day again.
 */
data class CardsWidgetPage(val page: Int, val at: Instant) {

    /** The page shown at [now] on the wall of [timeZone]: [page] until it expires, then 0. */
    fun current(now: Instant, timeZone: TimeZone): Int {
        val expired = now < at || now - at >= RESET_AFTER ||
            at.toLocalDateTime(timeZone).date != now.toLocalDateTime(timeZone).date
        return if (expired) 0 else page
    }

    /** How long until [current] returns 0 after [now], null when it already does. */
    fun resetIn(now: Instant, timeZone: TimeZone): Duration? =
        if (current(now, timeZone) == 0) null else at + RESET_AFTER - now

    companion object {
        /** A page other than the first holds this long after the last tap; a midnight ends it sooner. */
        val RESET_AFTER: Duration = 5.minutes
    }
}

/** The page each placed cards widget is turned to (Android: per `appWidgetId`). */
interface CardsWidgetPageStore {

    suspend fun get(widgetId: Int): CardsWidgetPage?

    suspend fun set(widgetId: Int, page: CardsWidgetPage)

    suspend fun clear(widgetId: Int)
}

/** The page each of [widgetIds] shows at [now]: its stored page until it expires, 0 otherwise. */
suspend fun CardsWidgetPageStore.currentPages(widgetIds: IntArray, now: Instant, timeZone: TimeZone): Map<Int, Int> =
    widgetIds.associateWith { get(it)?.current(now, timeZone) ?: 0 }

/** The soonest of [widgetIds] to return to its first page after [now], null when none is turned. */
suspend fun CardsWidgetPageStore.nextReset(widgetIds: IntArray, now: Instant, timeZone: TimeZone): Duration? =
    widgetIds.toList().mapNotNull { get(it)?.resetIn(now, timeZone) }.minOrNull()
