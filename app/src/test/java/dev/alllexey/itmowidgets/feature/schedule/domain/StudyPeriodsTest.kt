package dev.alllexey.itmowidgets.feature.schedule.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPeriodsTest {

    @Test
    fun `mid October starts the current autumn period and walks back through eight periods`() {
        assertEquals(listOf(
            date(2026, 9, 1)..date(2026, 10, 15),
            date(2026, 2, 1)..date(2026, 8, 31),
            date(2025, 9, 1)..date(2026, 1, 31),
            date(2025, 2, 1)..date(2025, 8, 31),
            date(2024, 9, 1)..date(2025, 1, 31),
            date(2024, 2, 1)..date(2024, 8, 31),
            date(2023, 9, 1)..date(2024, 1, 31),
            date(2023, 2, 1)..date(2023, 8, 31),
        ), StudyPeriods.recent(date(2026, 10, 15), 8))
    }

    @Test
    fun `31 January still belongs to the autumn period`() {
        assertEquals(listOf(
            date(2026, 9, 1)..date(2027, 1, 31),
            date(2026, 2, 1)..date(2026, 8, 31),
        ), StudyPeriods.recent(date(2027, 1, 31), 2))
    }

    @Test
    fun `1 February starts the spring period`() {
        assertEquals(listOf(
            date(2027, 2, 1)..date(2027, 2, 1),
            date(2026, 9, 1)..date(2027, 1, 31),
        ), StudyPeriods.recent(date(2027, 2, 1), 2))
    }

    @Test
    fun `31 August ends the spring period and 1 September starts the autumn one`() {
        assertEquals(date(2026, 2, 1)..date(2026, 8, 31), StudyPeriods.recent(date(2026, 8, 31), 1).single())
        assertEquals(listOf(
            date(2026, 9, 1)..date(2026, 9, 1),
            date(2026, 2, 1)..date(2026, 8, 31),
        ), StudyPeriods.recent(date(2026, 9, 1), 2))
    }

    @Test
    fun `returns exactly the requested number of periods`() {
        assertEquals(8, StudyPeriods.recent(date(2026, 3, 10), 8).size)
        assertEquals(1, StudyPeriods.recent(date(2026, 3, 10), 1).size)
    }

    private fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)
}
