package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate

/** The day a reader is looking at and how far into it they have scrolled ([LazyListState.firstVisibleItemScrollOffset]). */
internal data class ScheduleDayAnchor(val date: LocalDate, val offset: Int)

/**
 * Where [ScheduleRoute] puts its list when content arrives (the port of `ScheduleFragment`'s scroll helpers):
 *
 * - once per screen, on today with a peek of the day before, unless a saved position came back with the screen;
 * - after a switch to another user's schedule, on the day and offset read before ([anchorOn]); a day without lessons
 *   in the other schedule resolves to the next day it has, a day past its first page is paged in (up to
 *   [MAX_ANCHOR_PAGES] pages), and an unreachable day opens on today. While the anchored day is paged in the list is
 *   [positioning] (laid out, not drawn);
 * - on today again after the `Сегодня` request ([showToday]).
 *
 * Only [scrolledToToday] survives recreation, beside the list state itself: a restored position is never replaced
 * by today.
 */
@Stable
internal class ScheduleListPlacement(scrolledToToday: Boolean = false) {

    var scrolledToToday: Boolean = scrolledToToday
        private set

    private var anchor: ScheduleDayAnchor? by mutableStateOf(null)
    private var anchorUserIsu: Int? = null
    private var anchorPages = 0
    private var anchorPagedThrough: LocalDate? = null

    /** A day is being paged in after a switch: the list is laid out but not drawn. */
    val positioning: Boolean get() = anchor != null

    /** Bumped by [showToday], so a request on unchanged content places the list again. */
    var requests: Int by mutableIntStateOf(0)
        private set

    /**
     * The schedule switches to [userIsu] (null: the own one) while [visible] is on screen. An empty list (a denied or
     * a still loading schedule) keeps the previous anchor.
     */
    fun anchorOn(userIsu: Int?, visible: ScheduleDayAnchor?) {
        anchor = visible ?: anchor
        anchorUserIsu = userIsu
        anchorPages = 0
        anchorPagedThrough = null
    }

    /** The `Сегодня` shortcut: forget the anchor and the scroll, today comes next. */
    fun showToday() {
        anchor = null
        scrolledToToday = false
        requests++
    }

    /**
     * Places [listState] for [days], the dates the list shows of [userIsu]'s schedule. [loadingMore] holds an anchor
     * whose day may still arrive. Returns true when the anchored day needs the next page first: the caller asks for
     * it and places again once it has arrived.
     */
    suspend fun place(
        days: List<LocalDate>,
        userIsu: Int?,
        loadingMore: Boolean,
        today: LocalDate,
        todayPeek: Int,
        listState: LazyListState,
    ): Boolean {
        when (holdForAnchor(days, userIsu, loadingMore, listState)) {
            Hold.NONE -> Unit
            Hold.WAIT -> return false
            Hold.NEXT_PAGE -> return true
        }
        if (scrolledToToday) return false
        val index = days.indexOfFirst { it >= today }
        if (index == -1) return false
        listState.scrollToItem(index, -todayPeek)
        scrolledToToday = true
        return false
    }

    private suspend fun holdForAnchor(
        days: List<LocalDate>,
        userIsu: Int?,
        loadingMore: Boolean,
        listState: LazyListState,
    ): Hold {
        val target = anchor ?: return Hold.NONE
        // Content committed right after the switch can still be the previous schedule's.
        if (userIsu != anchorUserIsu) return Hold.WAIT
        val index = days.indexOfFirst { it >= target.date }
        if (index != -1) {
            listState.scrollToItem(index, target.offset)
            anchor = null
            scrolledToToday = true
            return Hold.NONE
        }
        // Another user's schedule starts from the first page again: a paged-in day needs its pages first.
        if (loadingMore) return Hold.WAIT
        val lastDate = days.lastOrNull()
        if (lastDate != null && lastDate != anchorPagedThrough && anchorPages < MAX_ANCHOR_PAGES) {
            anchorPagedThrough = lastDate
            anchorPages++
            return Hold.NEXT_PAGE
        }
        // An unreachable day: open on today, as a fresh screen does.
        anchor = null
        scrolledToToday = false
        return Hold.NONE
    }

    private enum class Hold { NONE, WAIT, NEXT_PAGE }

    companion object {
        const val MAX_ANCHOR_PAGES = 4

        val Saver: Saver<ScheduleListPlacement, Boolean> = Saver(
            save = { it.scrolledToToday },
            restore = { ScheduleListPlacement(scrolledToToday = it) },
        )
    }
}

/** The day at the top of [listState] while it shows [days], or null without a laid-out day. */
internal fun visibleDayAnchor(listState: LazyListState, days: List<LocalDate>): ScheduleDayAnchor? {
    if (listState.layoutInfo.totalItemsCount == 0) return null
    val date = days.getOrNull(listState.firstVisibleItemIndex) ?: return null
    return ScheduleDayAnchor(date, listState.firstVisibleItemScrollOffset)
}
