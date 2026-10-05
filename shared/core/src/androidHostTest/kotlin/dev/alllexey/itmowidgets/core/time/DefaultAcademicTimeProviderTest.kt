package dev.alllexey.itmowidgets.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

class DefaultAcademicTimeProviderTest {

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-07-18T09:34:56Z")
    }
    private val store = FakeAcademicTimeOverrideStore()
    private val provider = DefaultAcademicTimeProvider(clock, TimeZone.of("Europe/Moscow"), store)

    @Test
    fun `uses clock when override is absent`() {
        assertEquals(TimeZone.of("Europe/Moscow"), provider.timeZone)
        assertEquals(LocalDate(2026, 7, 18), provider.today())
        assertEquals(Instant.parse("2026-07-18T12:34:56+03:00"), provider.now())
        assertEquals(LocalDateTime(2026, 7, 18, 12, 34, 56), provider.localNow())
    }

    @Test
    fun `uses override date and preserves clock time`() {
        provider.setOverrideDate(LocalDate(2026, 2, 16))

        assertEquals(LocalDate(2026, 2, 16), provider.today())
        assertEquals(Instant.parse("2026-02-16T12:34:56+03:00"), provider.now())
        assertEquals(LocalDateTime(2026, 2, 16, 12, 34, 56), provider.localNow())
    }

    @Test
    fun `override keeps the wall time across the academic midnight`() {
        val lateClock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-07-18T21:30:00Z")
        }
        val late = DefaultAcademicTimeProvider(lateClock, TimeZone.of("Europe/Moscow"), store)

        assertEquals(LocalDate(2026, 7, 19), late.today())
        late.setOverrideDate(LocalDate(2026, 2, 16))

        assertEquals(LocalDate(2026, 2, 16), late.today())
        assertEquals(LocalDateTime(2026, 2, 16, 0, 30), late.localNow())
        assertEquals(Instant.parse("2026-02-15T21:30:00Z"), late.now())
    }

    @Test
    fun `clears override date`() {
        provider.setOverrideDate(LocalDate(2026, 2, 16))
        provider.setOverrideDate(null)

        assertNull(provider.getOverrideDate())
        assertEquals(LocalDate(2026, 7, 18), provider.today())
        assertEquals(Instant.parse("2026-07-18T09:34:56Z"), provider.now())
    }

    private class FakeAcademicTimeOverrideStore : AcademicTimeOverrideStore {
        private var date: LocalDate? = null

        override fun getOverrideDate(): LocalDate? = date

        override fun setOverrideDate(date: LocalDate?) {
            this.date = date
        }
    }
}
