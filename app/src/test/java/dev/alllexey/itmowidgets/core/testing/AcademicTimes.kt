package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

private val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

/** An academic clock standing still at [at] in Moscow. */
class FixedAcademicTime(
    private val at: LocalDateTime = LocalDateTime.of(LocalDate.of(2026, 10, 7), LocalTime.NOON)
) : AcademicTimeProvider {
    /** The start of [date]. */
    constructor(date: LocalDate) : this(date.atStartOfDay())

    override val zoneId: ZoneId = MOSCOW

    override fun today(): LocalDate = at.toLocalDate()

    override fun now(): OffsetDateTime = at.atZone(zoneId).toOffsetDateTime()
}

/** An academic clock in Moscow that a test moves by setting [current]. */
class MutableAcademicTime(var current: LocalDateTime) : AcademicTimeProvider {
    override val zoneId: ZoneId = MOSCOW

    override fun today(): LocalDate = current.toLocalDate()

    override fun now(): OffsetDateTime = current.atZone(zoneId).toOffsetDateTime()
}

/** The academic time of [clock] in Moscow, for tests that also hand the same clock to the code under test. */
class ClockAcademicTime(private val clock: Clock) : AcademicTimeProvider {
    override val zoneId: ZoneId = MOSCOW

    override fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), zoneId)

    override fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock.instant(), zoneId)
}
