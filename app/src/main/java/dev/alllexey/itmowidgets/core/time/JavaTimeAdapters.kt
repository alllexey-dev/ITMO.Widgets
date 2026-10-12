package dev.alllexey.itmowidgets.core.time

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaZoneId
import kotlin.time.toJavaInstant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

// App-only adapters for call sites still on java.time; the file goes when the last one moves to kotlinx-datetime.

/** [AcademicTimeProvider.today] as a `java.time.LocalDate`. */
fun AcademicTimeProvider.javaToday(): LocalDate = today().toJavaLocalDate()

/** [AcademicTimeProvider.now] at the academic offset, as `now()` returned before kotlinx-datetime. */
fun AcademicTimeProvider.javaNow(): OffsetDateTime = OffsetDateTime.ofInstant(now().toJavaInstant(), javaZone())

/** [AcademicTimeProvider.timeZone] as a `java.time.ZoneId`. */
fun AcademicTimeProvider.javaZone(): ZoneId = timeZone.toJavaZoneId()
