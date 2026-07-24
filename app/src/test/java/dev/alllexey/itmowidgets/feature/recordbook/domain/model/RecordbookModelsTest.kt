package dev.alllexey.itmowidgets.feature.recordbook.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordbookModelsTest {

    @Test
    fun `semester in course maps global semester number`() {
        assertEquals(
            1,
            RecordbookPeriod("2025/2026", semester = 1, course = 1, actual = false)
                .semesterInCourse
        )
        assertEquals(
            2,
            RecordbookPeriod("2025/2026", semester = 2, course = 1, actual = true)
                .semesterInCourse
        )
    }

    @Test
    fun `failed grade requires attention`() {
        val subject = subject(rate = "2/FX")
        assertEquals(RecordbookSubjectStatus.ATTENTION, subject.status)
        assertEquals(RecordbookRate.Grade("2FX"), subject.normalizedRate)
    }

    @Test
    fun `credit and passing grade are passed`() {
        assertEquals(RecordbookSubjectStatus.PASSED, subject(rate = "зачет").status)
        assertEquals(RecordbookSubjectStatus.PASSED, subject(rate = "4/C").status)
        assertEquals(RecordbookAssessmentKind.EXAM, subject(rate = "4/C").assessmentKind)
    }

    @Test
    fun `missing rate is in progress`() {
        assertEquals(RecordbookSubjectStatus.IN_PROGRESS, subject(rate = null).status)
    }

    @Test
    fun `classifies recordbook controls by normalized source name`() {
        val control = RecordbookControl(
            id = 1,
            name = "Домашнее задание",
            score = null,
            minimum = 0.0,
            maximum = 10.0,
            required = false,
            date = null,
            teacherName = null
        )

        assertEquals(RecordbookControlCategory.HOMEWORK, control.category)
    }

    private fun subject(rate: String?) = RecordbookSubject(
        name = "Test",
        disciplineId = 1,
        entryId = 2,
        controlType = "Экзамен",
        score = null,
        rate = rate,
        attempt = null,
        examDate = null,
        hasDetails = false,
        teacherName = null
    )
}
