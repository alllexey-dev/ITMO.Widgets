package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import java.time.OffsetDateTime
import org.junit.Assert.*
import org.junit.Test

class RecordbookSportBehindTest {
    private val endsAt = OffsetDateTime.parse("2026-12-27T00:00:00+03:00")

    private fun state(attendances: Int = 40, current: Boolean = true, endsAt: OffsetDateTime? = this.endsAt) =
        RecordbookSportState.Content("Осень 2026/2027", SportScoreSummary(attendances, 0), endsAt, current)

    @Test fun `shortfall at the start of the semester is not behind`() {
        assertFalse(isSportBehind(state(attendances = 4), OffsetDateTime.parse("2026-09-07T10:00:00+03:00")))
        assertFalse(isSportBehind(state(), endsAt.minusDays(28).minusSeconds(1)))
    }

    @Test fun `shortfall from 28 days before the end is behind`() {
        assertTrue(isSportBehind(state(), endsAt.minusDays(28)))
        assertTrue(isSportBehind(state(), endsAt.minusDays(3)))
        assertTrue(isSportBehind(state(), endsAt.plusDays(1)))
    }

    @Test fun `past semester with a shortfall is behind`() {
        assertTrue(isSportBehind(state(current = false, endsAt = null), OffsetDateTime.parse("2026-09-07T10:00:00+03:00")))
    }

    @Test fun `full score is never behind`() {
        assertFalse(isSportBehind(state(attendances = 100), endsAt.minusDays(1)))
        assertFalse(isSportBehind(state(attendances = 100, current = false, endsAt = null), endsAt))
    }

    @Test fun `unknown end of the current semester is not behind`() {
        assertFalse(isSportBehind(state(endsAt = null), endsAt.plusDays(1)))
    }
}
