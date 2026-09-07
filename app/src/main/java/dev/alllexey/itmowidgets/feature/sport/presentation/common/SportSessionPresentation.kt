package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import java.time.Duration
import java.time.OffsetDateTime

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

class SportSessionTiming(start: OffsetDateTime, end: OffsetDateTime, time: AcademicTimeProvider) {
    val start = start.atZoneSameInstant(time.zoneId)
    val end = end.atZoneSameInstant(time.zoneId)
    val durationMinutes: Long? = Duration.between(start, end).toMinutes().takeIf { it > 0 }
    val isToday = this.start.toLocalDate() == time.today()
    val isTomorrow = this.start.toLocalDate() == time.today().plusDays(1)
}
