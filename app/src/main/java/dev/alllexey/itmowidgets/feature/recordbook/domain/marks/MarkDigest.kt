package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.work.QuietHours
import java.time.LocalDateTime

/** One summary notification: every unread subject, newest first, and the record when there is only one. */
data class MarkDigest(val subjects: List<String>, val single: MarkNews?)

/** What to show now and which records count as delivered afterwards. */
data class MarkDigestDecision(val digest: MarkDigest?, val handled: Set<String>, val showPrompt: Boolean)

/** Chooses the notifications after a check. Pure: [decide] takes Moscow time from the caller. */
object MarkDigests {
    const val DIGEST_ID = 1
    const val PROMPT_ID = 2

    fun decide(news: List<MarkNews>, prompt: BarsLoginPrompt, now: LocalDateTime): MarkDigestDecision {
        if (QuietHours.isQuiet(now.toLocalTime())) return MarkDigestDecision(null, emptySet(), showPrompt = false)
        val pending = news.filter { !it.notified }
        // The digest replaces the previous one, so a subject it named before must stay in it.
        val digest = if (pending.isEmpty()) null else MarkDigest(
            subjects = news.sortedWith(compareByDescending<MarkNews> { it.detectedAt }.thenBy { it.name }).map { it.name },
            single = news.singleOrNull()
        )
        return MarkDigestDecision(digest, pending.mapTo(mutableSetOf()) { it.id }, prompt == BarsLoginPrompt.PENDING)
    }
}
