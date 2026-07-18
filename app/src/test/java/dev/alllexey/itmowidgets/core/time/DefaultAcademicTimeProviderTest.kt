package dev.alllexey.itmowidgets.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class DefaultAcademicTimeProviderTest {

    private val zoneId = ZoneId.of("Europe/Moscow")
    private val clock = Clock.fixed(
        Instant.parse("2026-07-18T09:34:56Z"),
        zoneId
    )
    private val store = FakeAcademicTimeOverrideStore()
    private val provider = DefaultAcademicTimeProvider(clock, store)

    @Test
    fun `uses clock when override is absent`() {
        assertEquals(LocalDate.of(2026, 7, 18), provider.today())
        assertEquals(
            OffsetDateTime.parse("2026-07-18T12:34:56+03:00"),
            provider.now()
        )
    }

    @Test
    fun `uses override date and preserves clock time`() {
        provider.setOverrideDate(LocalDate.of(2026, 2, 16))

        assertEquals(LocalDate.of(2026, 2, 16), provider.today())
        assertEquals(
            OffsetDateTime.parse("2026-02-16T12:34:56+03:00"),
            provider.now()
        )
    }

    @Test
    fun `clears override date`() {
        provider.setOverrideDate(LocalDate.of(2026, 2, 16))
        provider.setOverrideDate(null)

        assertNull(provider.getOverrideDate())
        assertEquals(LocalDate.of(2026, 7, 18), provider.today())
    }

    private class FakeAcademicTimeOverrideStore : AcademicTimeOverrideStore {
        private var date: LocalDate? = null

        override fun getOverrideDate(): LocalDate? = date

        override fun setOverrideDate(date: LocalDate?) {
            this.date = date
        }
    }
}
