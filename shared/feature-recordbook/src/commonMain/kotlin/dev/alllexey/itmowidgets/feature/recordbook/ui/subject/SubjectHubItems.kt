package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChip
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlEntry
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroup
import dev.alllexey.itmowidgets.feature.recordbook.domain.GradeStep
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControlRow
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacher
import dev.alllexey.itmowidgets.shared.core.links_chats
import dev.alllexey.itmowidgets.shared.core.links_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_controls_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_teachers_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/** One row of the subject page (port of 2.2's `SubjectHubAdapter.DetailItem`, LR-4a1). */
sealed interface SubjectHubItem {
    /** The result with the own sheet total, or the offer to connect one, at its bottom. */
    data class Hero(
        val subject: RecordbookSubject,
        val step: GradeStep?,
        val sheet: SubjectSheetState? = null,
    ) : SubjectHubItem
    data class SportOverview(val subject: RecordbookSubject, val sport: RecordbookSportState?) : SubjectHubItem
    /** A link of the short list under «Ссылки»: own ones carry «моя», others their votes. */
    data class Link(val link: SubjectLink, val canVote: Boolean, val position: GroupPosition) : SubjectHubItem
    /** The MyITMO LMS page of the subject, one of the short list. */
    data class Lms(val url: String, val position: GroupPosition) : SubjectHubItem
    /** «Все ссылки, N»: opens the links sheet. */
    data class AllLinks(val count: Int, val position: GroupPosition) : SubjectHubItem
    /** «Добавить ссылку» in place of «Все ссылки» while the subject has no links. */
    data class AddLink(val position: GroupPosition) : SubjectHubItem
    data class Chat(val link: SubjectLink, val position: GroupPosition) : SubjectHubItem
    /** A heading with its own string. */
    data class Section(val title: StringResource) : SubjectHubItem
    /** The heading of a control group with its sum; its controls follow as their own connected group. */
    data class Group(val group: ControlGroup) : SubjectHubItem
    /**
     * [date] is the control's date in the academic time zone; [spaced] parts a first row from a group of controls
     * right above it.
     */
    data class Control(
        val row: RecordbookControlRow,
        val date: LocalDate?,
        val subjectTeacher: String?,
        val position: GroupPosition,
        val spaced: Boolean = false,
    ) : SubjectHubItem
    /** No controls to show: [error] is the failure to load them, or null for «no details». */
    data class Notice(val error: StringResource?) : SubjectHubItem
    data class Lesson(val lesson: SubjectLesson, val position: GroupPosition) : SubjectHubItem
    /** «Все пары, N»: the rest of the window opens in place. */
    data class AllLessons(val count: Int, val position: GroupPosition) : SubjectHubItem
    /** Loading, empty, unmatched or failed lessons; never used for content. */
    data class LessonsMessage(val state: SubjectLessonsState) : SubjectHubItem
    data class BindingProposal(val candidate: ScheduleSubject) : SubjectHubItem
    data class BindingChoice(val candidates: List<ScheduleSubject>) : SubjectHubItem
    data class Teacher(
        val teacher: SubjectTeacher,
        val level: TeacherLevel?,
        val position: GroupPosition,
    ) : SubjectHubItem
}

/**
 * The whole subject page as one list (2.2's `SubjectHubAdapter.submitContent`): the result with the sheet total, links,
 * chats, controls, teachers and the nearest lessons. Every list section is a heading over one connected group.
 */
fun subjectHubItems(state: RecordbookSubjectUiState.Content): List<SubjectHubItem> {
    val hub = state.hub
    return buildList {
        if (state.subject.isPhysicalEducation) add(SubjectHubItem.SportOverview(state.subject, state.sport))
        else add(SubjectHubItem.Hero(state.subject, state.gradeStep, hub.sheet))
        if (hub.resourceScope != null) {
            add(SubjectHubItem.Section(CoreRes.string.links_title))
            addGroup(buildList {
                hub.chips.visible.forEach { chip ->
                    when (chip) {
                        is SubjectLinkChip.Link ->
                            add { position: GroupPosition -> SubjectHubItem.Link(chip.link, hub.canVote, position) }
                        is SubjectLinkChip.Lms ->
                            add { position: GroupPosition -> SubjectHubItem.Lms(chip.url, position) }
                    }
                }
                if (hub.linkCount > 0) {
                    add { position: GroupPosition -> SubjectHubItem.AllLinks(hub.linkCount, position) }
                } else {
                    add { position: GroupPosition -> SubjectHubItem.AddLink(position) }
                }
            })
        }
        if (hub.chats.isNotEmpty()) {
            add(SubjectHubItem.Section(CoreRes.string.links_chats))
            addGroup(hub.chats.map { link -> { position: GroupPosition -> SubjectHubItem.Chat(link, position) } })
        }
        if (state.controlsError != null || state.controls.isEmpty()) {
            // The sheet (or the offer of one) is the detail: «no details» goes, a failure to load the controls stays.
            // PE often has no control tree by design; it gets no empty card either.
            if (state.controlsError != null || (hub.sheet == null && !state.subject.isPhysicalEducation)) {
                add(SubjectHubItem.Notice(state.controlsError?.textResource()))
            }
        } else {
            add(SubjectHubItem.Section(Res.string.subject_controls_title))
            addControls(state)
        }
        if (hub.teachers.isNotEmpty()) {
            add(SubjectHubItem.Section(Res.string.subject_teachers_title))
            addGroup(hub.teachers.map { teacher ->
                { position: GroupPosition ->
                    SubjectHubItem.Teacher(teacher, teacher.isu?.let(hub.teacherLevels::get), position)
                }
            })
        }
        if (hub.lessons != SubjectLessonsState.Hidden) {
            add(SubjectHubItem.Section(Res.string.subject_lessons_title))
            when (val lessons = hub.lessons) {
                is SubjectLessonsState.Content -> addGroup(buildList {
                    hub.visibleLessons.forEach { lesson ->
                        add { position: GroupPosition -> SubjectHubItem.Lesson(lesson, position) }
                    }
                    if (hub.allLessonsCount > 0) {
                        add { position: GroupPosition -> SubjectHubItem.AllLessons(hub.allLessonsCount, position) }
                    }
                })
                is SubjectLessonsState.Proposed -> add(SubjectHubItem.BindingProposal(lessons.candidate))
                is SubjectLessonsState.Ambiguous -> add(SubjectHubItem.BindingChoice(lessons.candidates))
                else -> add(SubjectHubItem.LessonsMessage(lessons))
            }
        }
    }
}

/** Adds [rows] as one connected group: each row learns its place for the corners and gaps. */
private fun MutableList<SubjectHubItem>.addGroup(rows: List<(GroupPosition) -> SubjectHubItem>) =
    rows.forEachIndexed { index, row -> add(row(GroupPosition.of(index, rows.size))) }

/** Lone controls in a row share a group; each control group gets its heading and a group of its own. */
private fun MutableList<SubjectHubItem>.addControls(state: RecordbookSubjectUiState.Content) {
    val teacher = state.subject.teacherName
    fun RecordbookControl.localDate() = date?.toLocalDateTime(state.timeZone)?.date
    val singles = mutableListOf<ControlEntry.Single>()
    var afterGroup = false
    fun flushSingles() {
        if (singles.isEmpty()) return
        val spaced = afterGroup
        addGroup(singles.map { entry ->
            { position: GroupPosition ->
                val row = RecordbookControlRow(entry.control, 0)
                SubjectHubItem.Control(row, entry.control.localDate(), teacher, position, spaced)
            }
        })
        singles.clear()
    }
    state.controlGroups.forEach { entry ->
        when (entry) {
            is ControlEntry.Single -> singles += entry
            is ControlGroup -> {
                flushSingles()
                add(SubjectHubItem.Group(entry))
                addGroup(entry.controls.map { control ->
                    { position: GroupPosition ->
                        SubjectHubItem.Control(RecordbookControlRow(control, 1), control.localDate(), teacher, position)
                    }
                })
                afterGroup = true
            }
        }
    }
    flushSingles()
}
