package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import api.bars.model.Checkpoint
import api.bars.model.CheckpointPlan
import api.bars.model.StudentJournal
import api.bars.model.StudentMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject

/** No MyITMO identities, inferred grade thresholds or sums of aggregate and child marks. */
class BarsRecordbookMapper {
    fun subject(journal: StudentJournal, reference: BarsJournalReference, owner: String): RecordbookSubject {
        val plan = validatedPlan(journal, reference)
        val marks = ownMarks(journal, owner)
        val total = checkNotNull(marks.total)
        val approvals = marks.activeApprovals.orEmpty().filter {
            it.studentLogin == owner && it.checkpointPlanId == plan.id && it.isActive && !it.isInvalid && !it.isCourse
        }
        val latest = approvals.maxOfOrNull { it.attempt }?.let { attempt ->
            approvals.filter { it.attempt == attempt }.singleOrNull()
        }
        return RecordbookSubject(
            name = plan.discipline.name.trim(), disciplineId = plan.discipline.id,
            entryId = plan.id, controlType = plan.finalCheckpoint?.type?.trim().orEmpty(),
            // An untouched journal reports total 0; that is "nothing graded yet", not a score.
            score = total.takeIf { marks.hasAnyMark() && it.isFinite() },
            rate = latest?.gradeCode, attempt = latest?.attempt,
            examDate = null, hasDetails = true, teacherName = null, barsJournal = reference,
            absent = latest?.isAbsent == true
        )
    }

    fun controls(journal: StudentJournal, reference: BarsJournalReference, owner: String): List<RecordbookControl> {
        val plan = validatedPlan(journal, reference)
        val marks = ownMarks(journal, owner)
        val allMarks = marks.regular.orEmpty() + listOfNotNull(marks.finalMark)
        require(allMarks.all { it.checkpointPlanId == plan.id && it.mark != null })
        val byCheckpoint = allMarks.groupBy { it.checkpointId }
        return buildList {
            val visited = mutableSetOf<Long>()
            fun addCheckpoint(checkpoint: Checkpoint, parent: Long?, depth: Int) {
                require(depth < 20 && visited.add(checkpoint.id))
                val mark = byCheckpoint[checkpoint.id]?.singleOrNull()
                require(byCheckpoint[checkpoint.id].orEmpty().size <= 1)
                add(RecordbookControl(
                    checkpoint.id, checkpoint.name?.trim()?.takeIf(String::isNotEmpty) ?: checkpoint.type.trim(),
                    mark?.mark?.takeIf(Double::isFinite), checkpoint.minGrade.takeIf(Double::isFinite),
                    checkpoint.maxGrade.takeIf(Double::isFinite), checkpoint.isKey, null, null, parent,
                    absent = mark?.isAbsent == true
                ))
                checkpoint.subCheckpoints.orEmpty().forEach { addCheckpoint(it, checkpoint.id, depth + 1) }
            }
            plan.regularCheckpoints.orEmpty().forEach { addCheckpoint(it, null, 0) }
            plan.finalCheckpoint?.let { addCheckpoint(it, null, 0) }
            marks.additional?.let { extra ->
                require(extra.checkpointPlanId == plan.id)
                // The extra mark is plan-scoped and has checkpoint_id=null in the observed API.
                add(RecordbookControl(-plan.id, "", checkNotNull(extra.mark).takeIf(Double::isFinite),
                    null, null, false, null, null, absent = extra.isAbsent, additional = true))
            }
        }
    }

    private fun validatedPlan(journal: StudentJournal, reference: BarsJournalReference): CheckpointPlan {
        val plan = journal.headers.plan
        require(plan.id == reference.planId)
        require(journal.headers.type == reference.type && journal.headers.identifier == reference.identifier)
        require(plan.year == "${reference.yearStart}/${reference.yearStart + 1}")
        // A non-null course-project shape has not been established. Never silently omit its result.
        require(!plan.isCourseProject)
        return plan
    }

    private fun ownMarks(journal: StudentJournal, owner: String): StudentMarks {
        require(journal.students.size == 1)
        return journal.students.single().also { require(it.studentLogin == owner) }.marks
    }
}
