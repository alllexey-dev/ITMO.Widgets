package dev.alllexey.itmowidgets.core.time

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AcademicClock

interface AcademicTimeProvider {
    val zoneId: ZoneId

    fun today(): LocalDate

    fun now(): OffsetDateTime
}

interface AcademicTimeOverrideController {
    fun getOverrideDate(): LocalDate?

    fun setOverrideDate(date: LocalDate?)
}
