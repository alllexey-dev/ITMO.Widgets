package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class SportRegistrationStatus {
    SIGNED, AUTO_SIGNED, WAITING, NOTIFIED, FAILED, EXPIRED, CANCELLED, NOT_SIGNED;

    companion object {
        fun from(signed: Boolean, entry: SportQueueEntry?): SportRegistrationStatus = when {
            signed -> if (entry == null) SIGNED else AUTO_SIGNED
            entry == null -> NOT_SIGNED
            entry.isCancelled -> CANCELLED
            else -> when (entry.status) {
                SportQueueEntryStatus.WAITING -> WAITING
                SportQueueEntryStatus.NOTIFIED -> NOTIFIED
                SportQueueEntryStatus.SATISFIED -> AUTO_SIGNED
                SportQueueEntryStatus.GAVE_UP_NOTIFYING -> FAILED
                SportQueueEntryStatus.EXPIRED -> EXPIRED
            }
        }
    }
}

/** Invalid or predicted capacity is not rendered as an empty real lesson. */
data class SportOccupancy(val available: Int, val limit: Int) {
    val occupied: Int get() = limit - available

    companion object {
        fun from(isReal: Boolean, available: Int?, limit: Int?): SportOccupancy? =
            if (isReal && limit != null && limit > 0 && available != null && available in 0..limit) {
                SportOccupancy(available, limit)
            } else null
    }
}

/** A session's wall times in the academic zone, relative to the academic today. */
class SportSessionTiming(start: Instant, end: Instant, time: AcademicTimeProvider) {
    val start: LocalDateTime = start.toLocalDateTime(time.timeZone)
    val end: LocalDateTime = end.toLocalDateTime(time.timeZone)
    val durationMinutes: Long? = (end - start).inWholeMinutes.takeIf { it > 0 }
    val isToday = this.start.date == time.today()
    val isTomorrow = this.start.date == time.today().plus(1, DateTimeUnit.DAY)
}
