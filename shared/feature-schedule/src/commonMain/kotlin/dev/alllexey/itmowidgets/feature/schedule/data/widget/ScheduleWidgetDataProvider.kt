package dev.alllexey.itmowidgets.feature.schedule.data.widget

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetPreferences
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelection
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimeline
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineEntry
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class ScheduleWidgetDataProvider(
    private val repository: ScheduleRepository,
    private val scheduleChecks: ScheduleCheckPreferences,
    private val widgetSettings: WidgetSettingsPreferences,
    private val backend: BackendGate,
    private val timeProvider: AcademicTimeProvider,
    private val selector: ScheduleWidgetSelector,
    private val pendingBookings: PendingSportBookingsRepository,
    private val tokens: SessionTokenStore,
) {

    suspend fun load(): ScheduleWidgetLoadResult {
        val fetched = fetch { today, preferences ->
            if (preferences.display.full.showTomorrowWhenTodayIsOver) today.plus(1, DateTimeUnit.DAY) else today
        }
        return when (fetched) {
            is FetchedSchedule.SignedOut -> signedOut(fetched.preferences)
            is FetchedSchedule.Unavailable -> unavailable(fetched.preferences)
            is FetchedSchedule.Ready -> ScheduleWidgetLoadResult.Available(
                selector.select(
                    schedule = fetched.schedule,
                    now = fetched.now,
                    timeZone = timeProvider.timeZone,
                    preferences = fetched.preferences,
                    pendingSport = fetched.pendingSport
                ).let { selection ->
                    // Queue status can change remotely even before a lesson boundary.
                    if (fetched.pendingRequested) selection.copy(nextUpdateDelay = minOf(
                        selection.nextUpdateDelay, ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY
                    )) else selection
                }
            )
        }
    }

    /**
     * The widgets from now to [until] for a writer that cannot run [load] at every boundary (iOS WidgetKit, L18
     * IO-10b). Same sources and gates as [load]; Android renders [load]'s current snapshot. [until] lies after now.
     */
    suspend fun loadTimeline(until: Instant): ScheduleWidgetTimelineLoadResult {
        require(timeProvider.now() < until) { "Timeline ends at $until, which is not after now" }
        // The selector reads tomorrow of every instant, so the range ends the day after the last covered instant.
        val lastDay = (until - 1.nanoseconds).toLocalDateTime(timeProvider.timeZone).date
        val fetched = fetch { _, _ -> lastDay.plus(1, DateTimeUnit.DAY) }
        return when (fetched) {
            is FetchedSchedule.SignedOut -> ScheduleWidgetTimelineLoadResult.Available(
                singleEntry(signedOut(fetched.preferences).selection.snapshot, until)
            )
            is FetchedSchedule.Unavailable -> ScheduleWidgetTimelineLoadResult.Unavailable(
                singleLessonStyle = fetched.preferences.singleLessonStyle,
                lessonListStyle = fetched.preferences.lessonListStyle
            )
            is FetchedSchedule.Ready -> ScheduleWidgetTimelineLoadResult.Available(
                selector.timeline(
                    schedule = fetched.schedule,
                    from = fetched.now,
                    until = until,
                    timeZone = timeProvider.timeZone,
                    preferences = fetched.preferences,
                    pendingSport = fetched.pendingSport
                )
            )
        }
    }

    private suspend fun fetch(lastDate: (LocalDate, ScheduleWidgetPreferences) -> LocalDate): FetchedSchedule {
        val preferences = readPreferences()
        if (!tokens.hasRefreshToken()) return FetchedSchedule.SignedOut(preferences)
        val now = timeProvider.now()
        val start = now.toLocalDateTime(timeProvider.timeZone).date
        val end = lastDate(start, preferences)

        val includePending = pendingEnabled()
        val (refreshResult, pending) = coroutineScope {
            val optional = async { if (includePending) loadPending() else emptyList() }
            val official = repository.refreshSchedule(userIsu = null, startDate = start, endDate = end)
            official to optional.await()
        }
        if (!tokens.hasRefreshToken()) return FetchedSchedule.SignedOut(preferences)
        val cached = repository.observeScheduleForRange(
            userIsu = null,
            startDate = start,
            endDate = end
        ).first()

        if (refreshResult is AppResult.Failure && cached.isEmpty()) return FetchedSchedule.Unavailable(preferences)
        return FetchedSchedule.Ready(
            preferences = preferences,
            now = now,
            schedule = cached,
            pendingSport = if (pendingEnabled()) pending else emptyList(),
            pendingRequested = includePending
        )
    }

    private suspend fun loadPending(): List<PendingSportBooking> = try {
        pendingBookings.refresh()
        pendingBookings.getPendingBookings().valueOrNull().orEmpty()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun pendingEnabled(): Boolean =
        scheduleChecks.getScheduleSportAutoSignEnabled() && backend.isOptedIn()

    private fun signedOut(preferences: ScheduleWidgetPreferences) = ScheduleWidgetLoadResult.Available(
        ScheduleWidgetSelection(
            ScheduleWidgetSnapshot.signedOut(
                preferences.singleLessonStyle, preferences.lessonListStyle
            ).withTextSizes(preferences.display),
            ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY
        )
    )

    private fun unavailable(preferences: ScheduleWidgetPreferences) = ScheduleWidgetLoadResult.Unavailable(
        singleLessonStyle = preferences.singleLessonStyle,
        lessonListStyle = preferences.lessonListStyle
    )

    private fun singleEntry(snapshot: ScheduleWidgetSnapshot, until: Instant): ScheduleWidgetTimeline {
        val now = timeProvider.now()
        return ScheduleWidgetTimeline(
            generatedAt = now,
            validUntil = until,
            entries = listOf(ScheduleWidgetTimelineEntry(now, snapshot))
        )
    }

    private suspend fun readPreferences(): ScheduleWidgetPreferences {
        return ScheduleWidgetPreferences(
            smartScheduling = widgetSettings.getWidgetSmartSchedulingEnabled(),
            display = widgetSettings.getScheduleWidgetSettings(),
            singleLessonStyle = widgetSettings.getSingleLessonWidgetStyle(),
            lessonListStyle = widgetSettings.getLessonListWidgetStyle()
        )
    }
}

private sealed interface FetchedSchedule {
    val preferences: ScheduleWidgetPreferences

    data class SignedOut(override val preferences: ScheduleWidgetPreferences) : FetchedSchedule

    data class Unavailable(override val preferences: ScheduleWidgetPreferences) : FetchedSchedule

    data class Ready(
        override val preferences: ScheduleWidgetPreferences,
        val now: Instant,
        val schedule: List<DaySchedule>,
        val pendingSport: List<PendingSportBooking>,
        val pendingRequested: Boolean,
    ) : FetchedSchedule
}

sealed interface ScheduleWidgetLoadResult {

    data class Available(
        val selection: ScheduleWidgetSelection
    ) : ScheduleWidgetLoadResult

    data class Unavailable(
        val singleLessonStyle: LessonStyle,
        val lessonListStyle: LessonStyle,
    ) : ScheduleWidgetLoadResult
}

sealed interface ScheduleWidgetTimelineLoadResult {

    data class Available(
        val timeline: ScheduleWidgetTimeline
    ) : ScheduleWidgetTimelineLoadResult

    data class Unavailable(
        val singleLessonStyle: LessonStyle,
        val lessonListStyle: LessonStyle,
    ) : ScheduleWidgetTimelineLoadResult
}
