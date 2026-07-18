package dev.alllexey.itmowidgets.core.time

import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class DefaultAcademicTimeProvider(
    private val clock: Clock,
    private val overrideStore: AcademicTimeOverrideStore
) : AcademicTimeProvider, AcademicTimeOverrideController {

    override val zoneId: ZoneId
        get() = clock.zone

    override fun today(): LocalDate {
        return overrideStore.getOverrideDate() ?: LocalDate.now(clock)
    }

    override fun now(): OffsetDateTime {
        val realNow = OffsetDateTime.now(clock)
        val overrideDate = overrideStore.getOverrideDate() ?: return realNow
        return overrideDate
            .atTime(realNow.toLocalTime())
            .atZone(clock.zone)
            .toOffsetDateTime()
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
