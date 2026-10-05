package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

private val MOSCOW: TimeZone = TimeZone.of("Europe/Moscow")

/** An academic clock standing still at [at] in Moscow. */
class FixedAcademicTime(
    private val at: LocalDateTime = LocalDateTime.of(LocalDate.of(2026, 10, 7), LocalTime.NOON)
) : AcademicTimeProvider {
    /** The start of [date]. */
    constructor(date: LocalDate) : this(date.atStartOfDay())

    override val timeZone: TimeZone = MOSCOW

    override fun today(): kotlinx.datetime.LocalDate = at.toLocalDate().toKotlinLocalDate()

    override fun now(): Instant = at.toKotlinLocalDateTime().toInstant(timeZone)
}

/** An academic clock in Moscow that a test moves by setting [current]. */
class MutableAcademicTime(var current: LocalDateTime) : AcademicTimeProvider {
    override val timeZone: TimeZone = MOSCOW

    override fun today(): kotlinx.datetime.LocalDate = current.toLocalDate().toKotlinLocalDate()

    override fun now(): Instant = current.toKotlinLocalDateTime().toInstant(timeZone)
}

/** The academic time of [clock] in Moscow, for tests that also hand the same clock to the code under test. */
class ClockAcademicTime(private val clock: Clock) : AcademicTimeProvider {
    override val timeZone: TimeZone = MOSCOW

    override fun today(): kotlinx.datetime.LocalDate = now().toLocalDateTime(timeZone).date

    override fun now(): Instant = clock.instant().toKotlinInstant()
}
