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
    fun `uncredited variants are not counted as passed`() {
        listOf("Незачёт", "Не зачет", "не зачтено", " 2 / FX ").forEach {
            assertEquals(RecordbookSubjectStatus.ATTENTION, subject(it).status)
        }
        assertEquals(RecordbookRate.Grade("Незачёт"), subject("Незачёт").normalizedRate)
        assertEquals(RecordbookSubjectStatus.IN_PROGRESS, subject("неизвестно").status)
    }

    @Test
    fun `control outline preserves parent child order without dropping orphan or cycle`() {
        fun control(id: Long, parent: Long?) = RecordbookControl(id, "Работа $id", null, null, null, false, null, null, parent)
        val controls = listOf(control(2, 1), control(1, null), control(3, 99), control(4, 5), control(5, 4))
        val rows = recordbookControlOutline(controls)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), rows.map { it.control.id })
        assertEquals(listOf(0, 1, 0, 0, 1), rows.map { it.depth })
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
