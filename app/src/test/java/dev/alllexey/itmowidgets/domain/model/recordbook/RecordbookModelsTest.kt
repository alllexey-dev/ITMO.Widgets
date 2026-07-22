package dev.alllexey.itmowidgets.domain.model.recordbook

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
        assertEquals("2FX", subject.displayRate)
    }

    @Test
    fun `credit and passing grade are passed`() {
        assertEquals(RecordbookSubjectStatus.PASSED, subject(rate = "зачет").status)
        assertEquals(RecordbookSubjectStatus.PASSED, subject(rate = "4/C").status)
    }

    @Test
    fun `missing rate is in progress`() {
        assertEquals(RecordbookSubjectStatus.IN_PROGRESS, subject(rate = null).status)
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
