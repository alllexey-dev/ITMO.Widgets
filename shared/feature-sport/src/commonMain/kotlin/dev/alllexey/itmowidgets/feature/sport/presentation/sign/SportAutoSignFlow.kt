package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** What a tap on the queue button of a lesson leads to; the ViewModel turns it into a dialog. */
sealed interface SportAutoSignDecision {

    /** The custom-services opt-in is off: queues are Backend features. */
    data object ServicesDisabled : SportAutoSignDecision

    /** The lesson has an active queue entry: offer to leave it. */
    data class LeaveQueue(val position: Int, val total: Int, val command: SportSignCommand) : SportAutoSignDecision

    /** A real lesson: confirm a free-sign queue, with the force-sign switch. */
    data class ConfirmFreeSign(val command: SportSignCommand.CreateFreeSign) : SportAutoSignDecision

    /** A predicted lesson within the limit: confirm an auto-sign entry. */
    data class ConfirmAutoSign(val command: SportSignCommand.CreateAutoSign) : SportAutoSignDecision

    /** The predicted lesson's day already has an auto-sign entry, for [section] with [teacher]. */
    data class DayTaken(val section: String, val teacher: String) : SportAutoSignDecision

    /** The auto-sign limit is spent until [nextAvailable], formatted for display. */
    data class LimitReached(val nextAvailable: String) : SportAutoSignDecision

    data class Failed(val error: AppError) : SportAutoSignDecision
}

/**
 * The queue button of a lesson: what a tap on it leads to, and the queue command its dialog confirms.
 *
 * A lesson with an active queue entry offers to leave it; otherwise a real lesson offers a free-sign queue and a
 * predicted one an auto-sign entry, unless its day already has one or the limit is spent.
 */
class SportAutoSignFlow(
    private val bookingDelegate: SportBookingDelegate,
    private val timeProvider: AcademicTimeProvider
) {

    suspend fun onClick(lesson: SportLesson): SportAutoSignDecision {
        if (!bookingDelegate.areCommunityServicesEnabled()) return SportAutoSignDecision.ServicesDisabled
        return when (val entry = lesson.signEntry.takeIf { lesson.bookingConditions().hasActiveQueue }) {
            is SportFreeSignEntry ->
                SportAutoSignDecision.LeaveQueue(entry.position, entry.total, SportSignCommand.CancelFreeSign(entry.id))
            is SportAutoSignEntry ->
                SportAutoSignDecision.LeaveQueue(entry.position, entry.total, SportSignCommand.CancelAutoSign(entry.id))
            null -> if (lesson.isLessonReal) {
                SportAutoSignDecision.ConfirmFreeSign(SportSignCommand.CreateFreeSign(lesson.lessonId))
            } else {
                autoSignDecision(lesson)
            }
        }
    }

    suspend fun execute(command: SportSignCommand, forceSign: Boolean): AppResult<Unit> = when (command) {
        is SportSignCommand.CreateFreeSign -> bookingDelegate.createFreeSign(command.lessonId, forceSign)
        is SportSignCommand.CancelFreeSign -> bookingDelegate.cancelFreeSign(command.entryId)
        is SportSignCommand.CreateAutoSign -> bookingDelegate.createAutoSign(command.prototypeLessonId)
        is SportSignCommand.CancelAutoSign -> bookingDelegate.cancelAutoSign(command.entryId)
    }

    private suspend fun autoSignDecision(lesson: SportLesson): SportAutoSignDecision {
        val availability = when (val result = bookingDelegate.loadAutoSignAvailability()) {
            is AppResult.Failure -> return SportAutoSignDecision.Failed(result.error)
            is AppResult.Success -> result.value
        }
        val existingEntry = availability.entries
            .filterIsInstance<SportAutoSignEntry>()
            .find { it.targetLesson.start.academicDate() == lesson.start.academicDate() }

        return when {
            existingEntry != null -> SportAutoSignDecision.DayTaken(
                section = SectionName(existingEntry.targetLesson.sectionName).shorten(),
                teacher = existingEntry.targetLesson.teacherFio
            )

            availability.limits.available > 0 ->
                SportAutoSignDecision.ConfirmAutoSign(SportSignCommand.CreateAutoSign(lesson.lessonId))

            // Pinned to Russian "1 сент. 2026 г., 09:30:00" in the academic zone; java.time followed the
            // device locale and the offset Backend sent.
            else -> SportAutoSignDecision.LimitReached(
                availability.limits.nextAvailableAt
                    .toLocalDateTime(timeProvider.timeZone)
                    .format(DateTexts.LOCALIZED_MEDIUM_DATE_TIME)
            )
        }
    }

    private fun Instant.academicDate(): LocalDate = toLocalDateTime(timeProvider.timeZone).date
}
