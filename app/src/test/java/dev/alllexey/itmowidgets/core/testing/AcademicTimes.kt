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
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

private val MOSCOW: TimeZone = TimeZone.of("Europe/Moscow")

/** [FixedAcademicTime] at a java.time [at], for tests not yet on kotlinx-datetime. */
fun FixedAcademicTime(at: LocalDateTime): FixedAcademicTime = FixedAcademicTime(at.toKotlinLocalDateTime())

/** [FixedAcademicTime] at the start of a java.time [date]. */
fun FixedAcademicTime(date: LocalDate): FixedAcademicTime = FixedAcademicTime(date.toKotlinLocalDate())

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
