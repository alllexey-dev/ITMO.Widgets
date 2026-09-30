package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyHalfTest {

    @Test
    fun `autumn runs from September through January and spring from February through August`() {
        assertEquals("2025/2026-1", StudyHalf.of(LocalDate.of(2026, 1, 31)).key)
        assertEquals("2025/2026-2", StudyHalf.of(LocalDate.of(2026, 2, 1)).key)
        assertEquals("2025/2026-2", StudyHalf.of(LocalDate.of(2026, 8, 31)).key)
        assertEquals("2026/2027-1", StudyHalf.of(LocalDate.of(2026, 9, 1)).key)
    }

    @Test
    fun `a key parses back and anything else is null`() {
        val half = StudyHalf(2026, 1)

        assertEquals("2026/2027", half.studyYear)
        assertEquals(half, StudyHalf.parse(half.key))
        assertEquals(StudyHalf(2025, 2), StudyHalf.parse("2025/2026-2"))
        listOf("", "2026/2027", "2026/2027-3", "2026/2028-1", "2026-1", "x2026/2027-1").forEach {
            assertNull(it, StudyHalf.parse(it))
        }
    }

    @Test
    fun `a recordbook period knows its half only with a full study year`() {
        assertEquals(StudyHalf(2026, 1), RecordbookPeriod("2026/2027", 3, 2, actual = true).studyHalf())
        assertEquals(StudyHalf(2026, 2), RecordbookPeriod("2026/2027", 4, 2, actual = false).studyHalf())
        assertNull(RecordbookPeriod("2026", 3, 2, actual = true).studyHalf())
    }
}
