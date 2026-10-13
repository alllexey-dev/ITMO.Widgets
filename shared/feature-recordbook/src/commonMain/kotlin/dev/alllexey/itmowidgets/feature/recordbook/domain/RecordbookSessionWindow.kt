package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.studyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * When the recordbook shows the session (`Сдано N из M` and the average). My ITMO has no session dates, so: a period
 * of an earlier half always; the current one from its last weeks by the calendar (1 December in autumn, 15 May in
 * spring) or once an exam is at most four weeks ahead; a later one never.
 */
object RecordbookSessionWindow {
    fun isOpen(period: RecordbookPeriod, subjects: List<RecordbookSubject>, today: LocalDate, zone: TimeZone): Boolean {
        val half = period.studyHalf() ?: return false
        val current = StudyHalf.of(today)
        if (half != current) return half.yearStart < current.yearStart || (half.yearStart == current.yearStart && half.half < current.half)
        val lastWeeks = if (half.half == 1) LocalDate(half.yearStart, 12, 1) else LocalDate(half.yearStart + 1, 5, 15)
        if (today >= lastWeeks) return true
        val horizon = today.plus(EXAM_WEEKS_AHEAD, DateTimeUnit.WEEK)
        return subjects.any { subject -> subject.examDate?.toLocalDateTime(zone)?.date?.let { it <= horizon } == true }
    }

    private const val EXAM_WEEKS_AHEAD = 4
}
