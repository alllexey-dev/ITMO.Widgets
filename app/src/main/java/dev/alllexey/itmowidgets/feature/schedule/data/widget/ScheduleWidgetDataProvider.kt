package dev.alllexey.itmowidgets.feature.schedule.data.widget

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetPreferences
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelection
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ScheduleWidgetDataProvider @Inject constructor(
    private val repository: ScheduleRepository,
    private val settings: AppSettingsStorage,
    private val timeProvider: AcademicTimeProvider,
    private val selector: ScheduleWidgetSelector,
    private val pendingBookings: PendingSportBookingsRepository,
    private val tokens: SessionTokenStore,
) {

    suspend fun load(): ScheduleWidgetLoadResult {
        val preferences = readPreferences()
        if (!tokens.hasRefreshToken()) return signedOut(preferences)
        val now = timeProvider.now()
        val start = now.toLocalDate()
        val end = if (preferences.display.full.showTomorrowWhenTodayIsOver) start.plusDays(1) else start

        val includePending = pendingEnabled()
        val (refreshResult, pending) = coroutineScope {
            val optional = async { if (includePending) loadPending() else emptyList() }
            val official = repository.refreshSchedule(userIsu = null, startDate = start, endDate = end)
            official to optional.await()
        }
        if (!tokens.hasRefreshToken()) return signedOut(preferences)
        val cached = repository.observeScheduleForRange(
            userIsu = null,
            startDate = start,
            endDate = end
        ).first()

        if (refreshResult is AppResult.Failure && cached.isEmpty()) {
            return ScheduleWidgetLoadResult.Unavailable(
                singleLessonStyle = preferences.singleLessonStyle,
                lessonListStyle = preferences.lessonListStyle
            )
        }

        return ScheduleWidgetLoadResult.Available(
            selector.select(
                schedule = cached,
                now = now,
                preferences = preferences,
                pendingSport = if (pendingEnabled()) pending else emptyList()
            ).let { selection ->
                // Queue status can change remotely even before a lesson boundary.
                if (includePending) selection.copy(nextUpdateDelay = minOf(
                    selection.nextUpdateDelay, ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY
                )) else selection
            }
        )
    }

    private suspend fun loadPending(): List<PendingSportBooking> = try {
        pendingBookings.refresh()
        pendingBookings.getPendingBookings().dataOrNull().orEmpty()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun pendingEnabled(): Boolean =
        settings.getScheduleSportAutoSignEnabled() && settings.getCustomServicesEnabled()

    private fun signedOut(preferences: ScheduleWidgetPreferences) = ScheduleWidgetLoadResult.Available(
        ScheduleWidgetSelection(
            dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot.signedOut(
                preferences.singleLessonStyle, preferences.lessonListStyle
            ),
            ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY
        )
    )

    private suspend fun readPreferences(): ScheduleWidgetPreferences {
        return ScheduleWidgetPreferences(
            smartScheduling = settings.getWidgetSmartSchedulingEnabled(),
            display = settings.getScheduleWidgetSettings(),
            singleLessonStyle = settings.getSingleLessonWidgetStyle(),
            lessonListStyle = settings.getLessonListWidgetStyle()
        )
    }
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
