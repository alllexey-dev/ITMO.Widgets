package dev.alllexey.itmowidgets.core.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class StudyTermTest {

    @Test
    fun autumnRunsFromSeptemberThroughJanuaryAndSpringFromFebruaryThroughAugust() {
        assertEquals(StudyTerm(2025, autumn = true), StudyTerm.of(LocalDate(2025, 9, 1)))
        assertEquals(StudyTerm(2025, autumn = true), StudyTerm.of(LocalDate(2026, 1, 31)))
        assertEquals(StudyTerm(2026, autumn = false), StudyTerm.of(LocalDate(2026, 2, 1)))
        assertEquals(StudyTerm(2026, autumn = false), StudyTerm.of(LocalDate(2026, 8, 31)))
    }
}
