package dev.alllexey.itmowidgets.feature.schedule.domain.changes

import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.work.QuietHours
import java.time.LocalDateTime

/** One summary notification: how many changes are unread, which one it names and whether it makes a sound. */
data class ScheduleChangeDigest(val unread: Int, val first: ScheduleChange, val audible: Boolean)

/** What to show now, if anything, and which changes count as delivered afterwards. */
data class DigestDecision(val digest: ScheduleChangeDigest?, val handled: Set<String>)

/** Chooses the summary notification after a check. Pure: [decide] takes Moscow time from the caller. */
object ScheduleChangeDigests {
    const val NOTIFICATION_ID = 1

    fun decide(changes: List<ScheduleChange>, now: LocalDateTime): DigestDecision {
        if (QuietHours.isQuiet(now.toLocalTime())) return DigestDecision(null, emptySet())
        val pending = changes.filter { !it.read && !it.notified }
        val handled = pending.mapTo(mutableSetOf()) { it.id }
        val fresh = pending.filterNot { it.isOver(now) }
        if (fresh.isEmpty()) return DigestDecision(null, handled)

        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        val digest = ScheduleChangeDigest(
            unread = changes.count { !it.read && !it.isOver(now) },
            first = fresh.minWith(compareBy<ScheduleChange>({ it.soonestStart() }, { it.subjectName }, { it.id })),
            audible = fresh.any { it.touches(today) || it.touches(tomorrow) }
        )
        return DigestDecision(digest, handled)
    }
}
