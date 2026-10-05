package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** An academic clock standing still at [at] in Moscow. */
class FixedAcademicTime(
    private val at: LocalDateTime = LocalDateTime(LocalDate(2026, 10, 7), LocalTime(12, 0))
) : AcademicTimeProvider {
    /** The start of [date]. */
    constructor(date: LocalDate) : this(LocalDateTime(date, LocalTime(0, 0)))

    override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")

    override fun today(): LocalDate = at.date

    override fun now(): Instant = at.toInstant(timeZone)
}
