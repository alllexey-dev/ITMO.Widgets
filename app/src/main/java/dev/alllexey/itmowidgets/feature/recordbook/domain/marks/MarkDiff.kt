package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey

enum class MarkSource { MY_ITMO, BARS }

enum class MarkEventKind { MARK_ADDED, MARK_CHANGED, FINAL_CHANGED }

/** A new or changed mark of one subject; [nameKey] is what joins the sources and the unread list. */
data class MarkEvent(val source: MarkSource, val half: StudyHalf, val subjectName: String, val kind: MarkEventKind) {
    val nameKey: String get() = subjectNameKey(subjectName)
}

/** The snapshot to store, what changed against the previous one, and whether this was only a first look. */
data class Compared<S>(val snapshot: S, val events: List<MarkEvent>, val baseline: Boolean)

/**
 * Compares an answer with the last snapshot. Pure. What is missing from the answer is carried into the new snapshot
 * until the half-year changes, so one short answer never turns everything into news the next time. New subjects,
 * new plans and missing marks are not events; the order of the answer does not matter.
 */
object MarkDiff {

    fun myItmo(previous: MyItmoMarkSnapshot?, current: MyItmoMarkSnapshot): Compared<MyItmoMarkSnapshot> {
        if (previous == null || previous.half != current.half) return Compared(current, emptyList(), baseline = true)
        val previousByKey = previous.subjects.associateBy { it.key() }
        val currentKeys = current.subjects.mapTo(mutableSetOf()) { it.key() }
        val pairs = current.subjects.mapNotNull { subject -> previousByKey[subject.key()]?.let { it to subject } }
            .toMutableList()
        val missing = previous.subjects.filter { it.key() !in currentKeys }
        val unmatched = current.subjects.filter { it.key() !in previousByKey }
        // A row recreated under another est_id is the same subject when nothing else could be it.
        val recreated = unmatched.groupBy { it.identity() }.mapNotNull { (identity, fresh) ->
            val gone = missing.filter { it.identity() == identity }
            if (fresh.size == 1 && gone.size == 1) gone.single() to fresh.single() else null
        }
        pairs += recreated
        val relinked = recreated.mapTo(mutableSetOf()) { it.first.key() }
        val events = pairs.flatMap { (old, new) -> myItmoEvents(current.half, old, new) }
        val kept = missing.filter { it.key() !in relinked }
        return Compared(current.copy(subjects = current.subjects + kept), ordered(events), baseline = false)
    }

    fun bars(previous: BarsMarkSnapshot?, current: BarsMarkSnapshot): Compared<BarsMarkSnapshot> {
        if (previous == null || previous.half != current.half) return Compared(current, emptyList(), baseline = true)
        val previousById = previous.plans.associateBy { it.planId }
        val events = mutableListOf<MarkEvent>()
        val plans = current.plans.map { plan ->
            val old = previousById[plan.planId] ?: return@map plan
            events += barsEvents(current.half, old, plan)
            val present = plan.marks.mapTo(mutableSetOf()) { it.id }
            plan.copy(marks = plan.marks + old.marks.filter { it.id !in present })
        }
        val currentIds = current.plans.mapTo(mutableSetOf()) { it.planId }
        val kept = previous.plans.filter { it.planId !in currentIds }
        return Compared(current.copy(plans = plans + kept), ordered(events), baseline = false)
    }

    private fun myItmoEvents(half: StudyHalf, old: MyItmoSubjectMark, new: MyItmoSubjectMark): List<MarkEvent> =
        buildList {
            if (!sameScore(old.score, new.score) && !isEmptyScore(new.score)) {
                val kind = if (isEmptyScore(old.score)) MarkEventKind.MARK_ADDED else MarkEventKind.MARK_CHANGED
                add(MarkEvent(MarkSource.MY_ITMO, half, new.name, kind))
            }
            val rate = rateKey(new.rate)
            if (rate != null && rate != rateKey(old.rate)) {
                add(MarkEvent(MarkSource.MY_ITMO, half, new.name, MarkEventKind.FINAL_CHANGED))
            }
        }

    private fun barsEvents(half: StudyHalf, old: BarsPlanMarks, new: BarsPlanMarks): List<MarkEvent> = buildList {
        val oldMarks = old.marks.associateBy { it.id }
        new.marks.forEach { mark ->
            val before = oldMarks[mark.id]
            when {
                before == null -> add(MarkEvent(MarkSource.BARS, half, new.name, MarkEventKind.MARK_ADDED))
                !sameScore(before.mark, mark.mark) || before.absent != mark.absent ->
                    add(MarkEvent(MarkSource.BARS, half, new.name, MarkEventKind.MARK_CHANGED))
            }
        }
        val statement = Triple(rateKey(new.rate), new.attempt, new.absent)
        val statementChanged = statement != Triple(rateKey(old.rate), old.attempt, old.absent)
        if (statementChanged && (statement.first != null || new.absent)) {
            add(MarkEvent(MarkSource.BARS, half, new.name, MarkEventKind.FINAL_CHANGED))
        }
    }

    private fun ordered(events: List<MarkEvent>): List<MarkEvent> =
        events.distinct().sortedWith(compareBy<MarkEvent>({ it.nameKey }, { it.kind }))

    private fun isEmptyScore(score: Double?): Boolean = score == null || score == 0.0

    private fun MyItmoSubjectMark.key() = Triple(programId, semester, entryId)

    private data class Identity(val programId: Long, val semester: Int, val disciplineId: Long, val nameKey: String)

    private fun MyItmoSubjectMark.identity() = Identity(programId, semester, disciplineId, subjectNameKey(name))
}
