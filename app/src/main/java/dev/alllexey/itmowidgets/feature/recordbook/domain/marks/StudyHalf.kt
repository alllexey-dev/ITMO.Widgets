package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import java.time.LocalDate

/**
 * A half of a study year: [half] 1 is autumn (September–January), 2 is spring (February–August). My ITMO periods and
 * BARS terms of one half are the same marks.
 */
data class StudyHalf(val yearStart: Int, val half: Int) {
    val studyYear: String get() = "$yearStart/${yearStart + 1}"
    val key: String get() = "$studyYear-$half"

    companion object {
        private val KEY = Regex("""(\d{4})/(\d{4})-([12])""")

        /** The half [today] belongs to; [today] comes from `AcademicTimeProvider`. */
        fun of(today: LocalDate): StudyHalf = StudyHalf(
            yearStart = today.year - if (today.monthValue < 9) 1 else 0,
            half = if (today.monthValue in 2..8) 2 else 1
        )

        /** The inverse of [key]; null for anything else. */
        fun parse(key: String): StudyHalf? {
            val match = KEY.matchEntire(key) ?: return null
            val (start, end, half) = match.destructured
            if (end.toInt() != start.toInt() + 1) return null
            return StudyHalf(start.toInt(), half.toInt())
        }
    }
}

private val STUDY_YEAR = Regex("""(\d{4})/\d{4}""")

/** Null when My ITMO's study year is not `2026/2027`-shaped. */
fun RecordbookPeriod.studyHalf(): StudyHalf? =
    STUDY_YEAR.matchEntire(studyYear)?.let { StudyHalf(it.groupValues[1].toInt(), semesterInCourse) }
