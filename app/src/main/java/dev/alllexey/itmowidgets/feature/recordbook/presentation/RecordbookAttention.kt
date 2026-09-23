package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.isBelowMinimum
import dev.alllexey.itmowidgets.feature.recordbook.domain.isSportBehind
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import java.time.OffsetDateTime

/** Why a subject is listed under «Требуют внимания»; the row shows it instead of the assessment kind. */
sealed interface RecordbookAttentionReason {
    data object Failed : RecordbookAttentionReason
    data object Absent : RecordbookAttentionReason
    data class BelowMinimum(val controlName: String) : RecordbookAttentionReason
    data class SportShort(val remaining: Int) : RecordbookAttentionReason
}

/**
 * A passed subject never needs attention. [controls] are the ones already known for the subject
 * (the list never asks for them); a graded control under its minimum is the most precise reason.
 * Physical education falls behind only near the end of its sport period, see [isSportBehind].
 */
fun attentionReason(
    subject: RecordbookSubject,
    sport: RecordbookSportState?,
    controls: List<RecordbookControl>?,
    now: OffsetDateTime
): RecordbookAttentionReason? {
    if (subject.status == RecordbookSubjectStatus.PASSED) return null
    if (subject.absent) return RecordbookAttentionReason.Absent
    controls?.firstOrNull { !it.additional && it.isBelowMinimum }
        ?.let { return RecordbookAttentionReason.BelowMinimum(it.name) }
    if (subject.status == RecordbookSubjectStatus.ATTENTION) return RecordbookAttentionReason.Failed
    val content = (sport as? RecordbookSportState.Content)?.takeIf { subject.isPhysicalEducation } ?: return null
    return if (isSportBehind(content, now)) RecordbookAttentionReason.SportShort(content.score.remaining) else null
}
