package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject

/** No MyITMO identities, inferred grade thresholds or sums of aggregate and child marks. */
class BarsRecordbookMapper {
    fun subject(journal: BarsJournal, reference: BarsJournalReference, owner: String): RecordbookSubject {
        val plan = validatedPlan(journal, reference)
        val marks = ownMarks(journal, owner)
        val total = checkNotNull(marks.total)
        // An untouched journal reports total 0; that is "nothing graded yet", not a score.
        val graded = marks.regular.isNotEmpty() || marks.finalMark != null || marks.additional != null
        val approvals = marks.approvals.filter {
            it.login == owner && it.planId == plan.id && it.active && !it.invalid && !it.course
        }
        val latest = approvals.maxOfOrNull { it.attempt }?.let { attempt ->
            approvals.filter { it.attempt == attempt }.singleOrNull()
        }
        return RecordbookSubject(
            name = plan.discipline.name.trim(), disciplineId = plan.discipline.id,
            entryId = plan.id, controlType = plan.finalCheckpoint?.type?.trim().orEmpty(),
            score = total.takeIf { graded && it.isFinite() },
            rate = latest?.rate?.let(::recordbookRate), attempt = latest?.attempt,
            examDate = null, hasDetails = true, teacherName = null, barsJournal = reference,
            absent = latest?.absent == true
        )
    }

    fun controls(journal: BarsJournal, reference: BarsJournalReference, owner: String): List<RecordbookControl> {
        val plan = validatedPlan(journal, reference)
        val marks = ownMarks(journal, owner)
        val allMarks = marks.regular + listOfNotNull(marks.finalMark)
        require(allMarks.all { it.planId == plan.id && it.mark != null })
        val byCheckpoint = allMarks.groupBy { it.checkpointId }
        return buildList {
            val visited = mutableSetOf<Long>()
            fun addCheckpoint(checkpoint: BarsCheckpoint, parent: Long?, depth: Int) {
                require(depth < 20 && visited.add(checkpoint.id))
                val mark = byCheckpoint[checkpoint.id]?.singleOrNull()
                require(byCheckpoint[checkpoint.id].orEmpty().size <= 1)
                add(RecordbookControl(
                    checkpoint.id, checkpoint.name?.trim()?.takeIf(String::isNotEmpty) ?: checkpoint.type.trim(),
                    mark?.mark?.takeIf(Double::isFinite), checkpoint.minimum.takeIf(Double::isFinite),
                    checkpoint.maximum.takeIf(Double::isFinite), checkpoint.key, null, null, parent,
                    absent = mark?.absent == true
                ))
                checkpoint.children.forEach { addCheckpoint(it, checkpoint.id, depth + 1) }
            }
            plan.regular.forEach { addCheckpoint(it, null, 0) }
            plan.finalCheckpoint?.let { addCheckpoint(it, null, 0) }
            marks.additional?.let { extra ->
                require(extra.planId == plan.id)
                // The extra mark is plan-scoped and has checkpoint_id=null in the observed API.
                add(RecordbookControl(-plan.id, "", checkNotNull(extra.mark).takeIf(Double::isFinite),
                    null, null, false, null, null, absent = extra.absent, additional = true))
            }
        }
    }

    /** BARS writes «Удвл., E»; the recordbook model and MyITMO use «3/E». Credits stay words. */
    private fun recordbookRate(markString: String): String {
        val text = markString.trim()
        val parts = text.split(',', limit = 2).map(String::trim)
        val digit = when (parts[0].lowercase().replace('ё', 'е').trimEnd('.')) {
            "отл" -> 5
            "хор" -> 4
            "удвл", "удовл" -> 3
            "неуд" -> 2
            else -> return text
        }
        val letter = parts.getOrNull(1)?.uppercase()?.takeIf { it.matches(Regex("[A-FX]{1,2}")) } ?: return text
        return "$digit/$letter"
    }

    private fun validatedPlan(journal: BarsJournal, reference: BarsJournalReference): BarsPlan {
        require(journal.headers.plan.id == reference.planId)
        require(journal.headers.type == reference.type && journal.headers.identifier == reference.identifier)
        require(journal.headers.plan.year == "${reference.yearStart}/${reference.yearStart + 1}")
        // A non-null course-project shape has not been established. Never silently omit its result.
        require(!journal.headers.plan.hasCourseProject)
        return journal.headers.plan
    }

    private fun ownMarks(journal: BarsJournal, owner: String): BarsMarks {
        require(journal.students.size == 1)
        return journal.students.single().also { require(it.login == owner) }.marks
    }
}
