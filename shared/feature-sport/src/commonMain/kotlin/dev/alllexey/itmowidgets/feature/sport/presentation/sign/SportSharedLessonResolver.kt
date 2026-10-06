package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.findLinked
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import kotlinx.coroutines.flow.first

/**
 * Finds the lesson of a shared link in the merged catalog and remembers it, so its card keeps acting on the lesson
 * while filters hide it from the list.
 */
class SportSharedLessonResolver(
    private val scheduleRepository: SportScheduleRepository,
    private val timeProvider: AcademicTimeProvider
) {

    /** The id and reality of the lesson a link opened. */
    private var linkedKey: Pair<Long, Boolean>? = null

    /**
     * Waits for the first answer of the merged catalog and returns what the link opens. Filters are ignored; a lesson
     * that has ended or is not in the catalog is unavailable; with [predicted] the id names the prototype, see
     * [findLinked].
     */
    suspend fun resolve(lessonId: Long, predicted: Boolean): SportSignEvent {
        val state = scheduleRepository.observeSportSchedule()
            .first { it.valueOrNull() != null || it.errorOrNull() != null }
        val lessons = state.valueOrNull() ?: return SportSignEvent.ShowError(checkNotNull(state.errorOrNull()))
        val lesson = lessons.findLinked(lessonId, predicted)?.takeIf { it.end > timeProvider.now() }
            ?: return SportSignEvent.ShowLinkUnavailable
        linkedKey = lesson.lessonId to lesson.isLessonReal
        return SportSignEvent.OpenLessonDetails(lesson)
    }

    /** The current state in [catalog] of the lesson a link opened, also when filters hide it from the list. */
    fun linkedLesson(lessonId: Long, catalog: List<SportLesson>): SportLesson? {
        val (id, real) = linkedKey?.takeIf { it.first == lessonId } ?: return null
        return catalog.firstOrNull { it.lessonId == id && it.isLessonReal == real }
    }
}
