package dev.alllexey.itmowidgets.feature.schedule.data.widget

import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleDays
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetPreferences
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelection
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimeline
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineEntry
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The schedule widgets' data: the own [ScheduleDays] of today and the week ahead (the days Сегодня draws, with their changes
 * and, while the queue rows are on, the sport queues), signed-out and unavailable states. [load] with `refresh` fetches
 * first, as every scheduled update does; without it, for the minute ticks of Android's countdown and `сейчас` line, it
 * reads the cache only and starts no request.
 */
class ScheduleWidgetDataProvider(
    private val days: ScheduleDays,
    private val scheduleChecks: ScheduleCheckPreferences,
    private val widgetSettings: WidgetSettingsPreferences,
    private val backend: BackendGate,
    private val timeProvider: AcademicTimeProvider,
    private val selector: ScheduleWidgetSelector,
    private val tokens: SessionTokenStore,
) {

    suspend fun load(refresh: Boolean = true): ScheduleWidgetLoadResult {
        val fetched = fetch(refresh) { today -> today.plus(ScheduleWidgetSelector.WEEK_AHEAD, DateTimeUnit.DAY) }
        return when (fetched) {
            FetchedSchedule.SignedOut -> signedOut()
            FetchedSchedule.Unavailable -> ScheduleWidgetLoadResult.Unavailable
            is FetchedSchedule.Ready -> ScheduleWidgetLoadResult.Available(
                selector.select(fetched.days, fetched.now, timeProvider.timeZone, fetched.preferences).let { selection ->
                    // A shown queue's position can change remotely before a lesson boundary; its booking arrives by push.
                    if (selection.snapshot.pendingValidUntil != null && pendingEnabled()) {
                        selection.copy(nextUpdateDelay = minOf(selection.nextUpdateDelay, ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY))
                    } else selection
                }
            )
        }
    }

    /**
     * The widgets from now to [until] for a writer that cannot run [load] at every boundary (iOS WidgetKit). Same
     * sources and gates as [load]; Android renders [load]'s current snapshot. [until] lies after now.
     */
    suspend fun loadTimeline(until: Instant): ScheduleWidgetTimelineLoadResult {
        require(timeProvider.now() < until) { "Timeline ends at $until, which is not after now" }
        // The selector reads the week ahead of every instant, so the range ends that far after the last covered instant.
        val lastDay = (until - 1.nanoseconds).toLocalDateTime(timeProvider.timeZone).date
        return when (val fetched = fetch(refresh = true) { lastDay.plus(ScheduleWidgetSelector.WEEK_AHEAD, DateTimeUnit.DAY) }) {
            FetchedSchedule.SignedOut -> ScheduleWidgetTimelineLoadResult.Available(
                singleEntry(signedOut().selection.snapshot, until)
            )
            FetchedSchedule.Unavailable -> ScheduleWidgetTimelineLoadResult.Unavailable
            is FetchedSchedule.Ready -> ScheduleWidgetTimelineLoadResult.Available(
                selector.timeline(fetched.days, fetched.now, until, timeProvider.timeZone, fetched.preferences)
            )
        }
    }

    private suspend fun fetch(refresh: Boolean, lastDate: (LocalDate) -> LocalDate): FetchedSchedule {
        if (!tokens.hasRefreshToken()) return FetchedSchedule.SignedOut
        val preferences = ScheduleWidgetPreferences(widgetSettings.getScheduleWidgetSettings())
        val now = timeProvider.now()
        val start = now.toLocalDateTime(timeProvider.timeZone).date
        val end = lastDate(start)
        val refreshed = if (refresh) days.refresh(userIsu = null, from = start, to = end) else null
        if (!tokens.hasRefreshToken()) return FetchedSchedule.SignedOut
        val cached = days.observe(userIsu = null, from = start, to = end).first()
        if (refreshed is AppResult.Failure && cached.isEmpty()) return FetchedSchedule.Unavailable
        return FetchedSchedule.Ready(preferences, now, cached)
    }

    private suspend fun pendingEnabled(): Boolean =
        scheduleChecks.getScheduleSportAutoSignEnabled() && backend.isOptedIn()

    private suspend fun signedOut() = ScheduleWidgetLoadResult.Available(
        ScheduleWidgetSelection(
            ScheduleWidgetSnapshot.signedOut().withTextSizes(widgetSettings.getScheduleWidgetSettings()),
            ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY
        )
    )

    private fun singleEntry(snapshot: ScheduleWidgetSnapshot, until: Instant): ScheduleWidgetTimeline {
        val now = timeProvider.now()
        return ScheduleWidgetTimeline(
            generatedAt = now,
            validUntil = until,
            entries = listOf(ScheduleWidgetTimelineEntry(now, snapshot))
        )
    }
}

private sealed interface FetchedSchedule {
    data object SignedOut : FetchedSchedule

    data object Unavailable : FetchedSchedule

    data class Ready(
        val preferences: ScheduleWidgetPreferences,
        val now: Instant,
        val days: List<LessonDay>,
    ) : FetchedSchedule
}

sealed interface ScheduleWidgetLoadResult {

    data class Available(val selection: ScheduleWidgetSelection) : ScheduleWidgetLoadResult

    /** Nothing cached and the refresh failed: the caller keeps its last snapshot. */
    data object Unavailable : ScheduleWidgetLoadResult
}

sealed interface ScheduleWidgetTimelineLoadResult {

    data class Available(val timeline: ScheduleWidgetTimeline) : ScheduleWidgetTimelineLoadResult

    data object Unavailable : ScheduleWidgetTimelineLoadResult
}
