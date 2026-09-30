package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlin.math.abs

/** One row of My ITMO's recordbook list; [entryId] is `est_id`. */
data class MyItmoSubjectMark(
    val programId: Long,
    val semester: Int,
    val entryId: Long,
    val disciplineId: Long,
    val name: String,
    val score: Double?,
    val rate: String?
)

/** My ITMO's marks of one half-year, every program's matching periods together. */
data class MyItmoMarkSnapshot(val half: StudyHalf, val subjects: List<MyItmoSubjectMark>)

/** A graded or missed checkpoint; the additional points of a plan have the id `-planId`. */
data class BarsCheckpointMark(val id: Long, val mark: Double?, val absent: Boolean)

/** The own marks of one BARS journal: its checkpoints and the last unambiguous statement. */
data class BarsPlanMarks(
    val planId: Long,
    val type: String,
    val identifier: String,
    val name: String,
    val score: Double?,
    val rate: String?,
    val attempt: Int?,
    val absent: Boolean,
    val marks: List<BarsCheckpointMark>
) {
    companion object
}

/** The BARS journals of one half-year. */
data class BarsMarkSnapshot(val half: StudyHalf, val plans: List<BarsPlanMarks>)

fun RecordbookSubject.toMyItmoMark(programId: Long, semester: Int): MyItmoSubjectMark =
    MyItmoSubjectMark(programId, semester, entryId, disciplineId, name, score, rate)

/** Built from what the BARS mapper made of a journal; null for a subject without a journal. */
fun BarsPlanMarks.Companion.of(journal: RecordbookSubject, controls: List<RecordbookControl>): BarsPlanMarks? {
    val reference = journal.barsJournal ?: return null
    return BarsPlanMarks(
        planId = reference.planId,
        type = reference.type,
        identifier = reference.identifier,
        name = journal.name,
        score = journal.score,
        rate = journal.rate,
        attempt = journal.attempt,
        absent = journal.absent,
        marks = controls.filter { it.score != null || it.absent }.map { BarsCheckpointMark(it.id, it.score, it.absent) }
    )
}

/** No score and zero are the same; closer than half a hundredth is equal. */
internal fun sameScore(a: Double?, b: Double?): Boolean = abs((a ?: 0.0) - (b ?: 0.0)) < SCORE_EPSILON

/** `4/C` and ` 4C ` are one grade, `Зачёт` and `зачет` too; an empty grade is none. */
internal fun rateKey(rate: String?): String? =
    rate?.trim()?.lowercase()?.replace('ё', 'е')?.replace(Regex("""[\s/]"""), "")?.takeIf { it.isNotEmpty() }

private const val SCORE_EPSILON = 0.005
