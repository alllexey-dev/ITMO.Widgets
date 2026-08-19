package dev.alllexey.itmowidgets.feature.schedule.data.widget

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetPreferences
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelection
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ScheduleWidgetDataProvider @Inject constructor(
    private val repository: ScheduleRepository,
    private val settings: AppSettingsStorage,
    private val timeProvider: AcademicTimeProvider,
    private val selector: ScheduleWidgetSelector,
) {

    suspend fun load(): ScheduleWidgetLoadResult {
        val preferences = readPreferences()
        val now = timeProvider.now()
        val start = now.toLocalDate()
        val end = if (preferences.showTomorrowWhenFinished) start.plusDays(1) else start

        val refreshResult = repository.refreshSchedule(
            userIsu = null,
            startDate = start,
            endDate = end
        )
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
                preferences = preferences
            )
        )
    }

    private suspend fun readPreferences(): ScheduleWidgetPreferences {
        return ScheduleWidgetPreferences(
            smartScheduling = settings.getWidgetSmartSchedulingEnabled(),
            forwardScheduling = settings.getWidgetForwardSchedulingEnabled(),
            hideTeacher = settings.getWidgetHideTeacherEnabled(),
            hidePreviousLessons = settings.getWidgetHidePreviousLessonsEnabled(),
            showTomorrowWhenFinished = settings.getWidgetFutureScheduleEnabled(),
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
