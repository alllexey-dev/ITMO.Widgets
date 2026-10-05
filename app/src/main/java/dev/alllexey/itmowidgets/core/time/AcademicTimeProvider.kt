package dev.alllexey.itmowidgets.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The academic time: the wall clock in [timeZone] (Europe/Moscow), or a debug override date at the real wall time. */
interface AcademicTimeProvider {
    val timeZone: TimeZone

    fun today(): LocalDate

    fun now(): Instant

    fun localNow(): LocalDateTime = now().toLocalDateTime(timeZone)
}

interface AcademicTimeOverrideController {
    fun getOverrideDate(): LocalDate?

    fun setOverrideDate(date: LocalDate?)
}
