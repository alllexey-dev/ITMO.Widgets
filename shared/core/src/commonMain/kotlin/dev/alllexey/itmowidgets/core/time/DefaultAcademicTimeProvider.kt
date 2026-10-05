package dev.alllexey.itmowidgets.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** With an override date, the academic time is that date at the real wall time in [timeZone]. */
class DefaultAcademicTimeProvider(
    private val clock: Clock,
    override val timeZone: TimeZone,
    private val overrideStore: AcademicTimeOverrideStore
) : AcademicTimeProvider, AcademicTimeOverrideController {

    override fun today(): LocalDate {
        return overrideStore.getOverrideDate() ?: clock.now().toLocalDateTime(timeZone).date
    }

    override fun now(): Instant {
        val realNow = clock.now()
        val overrideDate = overrideStore.getOverrideDate() ?: return realNow
        return LocalDateTime(overrideDate, realNow.toLocalDateTime(timeZone).time).toInstant(timeZone)
    }

    override fun localNow(): LocalDateTime {
        val realNow = clock.now().toLocalDateTime(timeZone)
        val overrideDate = overrideStore.getOverrideDate() ?: return realNow
        return LocalDateTime(overrideDate, realNow.time)
    }

    override fun getOverrideDate(): LocalDate? {
        return overrideStore.getOverrideDate()
    }

    override fun setOverrideDate(date: LocalDate?) {
        overrideStore.setOverrideDate(date)
    }
}

interface AcademicTimeOverrideStore {
    fun getOverrideDate(): LocalDate?

    fun setOverrideDate(date: LocalDate?)
}
