package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.widget.Button
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.ui.buildingShortTitle
import dev.alllexey.itmowidgets.core.ui.lessonTypeColorRes
import dev.alllexey.itmowidgets.core.ui.lessonTypeNameRes
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.roomShortTitle
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemRecordbookControlBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookNoteBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookOverviewBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSectionBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSportBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectBindingBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectLessonBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectMessageBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectResourceBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectTeacherBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControlRow
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.recordbookControlOutline
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookProgress
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectResource
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacher
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface DetailItem {
    data class Overview(val subject: RecordbookSubject) : DetailItem
    data class SportOverview(val subject: RecordbookSubject, val sport: RecordbookSportState?) : DetailItem
    data object Heading : DetailItem
    data class Control(val row: RecordbookControlRow, val subjectTeacher: String?) : DetailItem
    data class Notice(val errorRes: Int?) : DetailItem
    /** Hub sections: a heading with its own string, then one of the rows below. */
    data class Section(val titleRes: Int) : DetailItem
    data class Lesson(val lesson: SubjectLesson) : DetailItem
    /** Loading, empty, unmatched or failed lessons; never used for content. */
    data class LessonsMessage(val state: SubjectLessonsState) : DetailItem
    data class BindingProposal(val candidate: ScheduleSubject) : DetailItem
    data class BindingChoice(val candidates: List<ScheduleSubject>) : DetailItem
    data class Teacher(val teacher: SubjectTeacher) : DetailItem
    data class Resource(val resource: SubjectResource) : DetailItem
}

/** Hub actions the subject screen forwards to its view model. */
data class SubjectHubActions(
    val onConfirmBinding: (Long) -> Unit = {},
    val onRejectProposal: () -> Unit = {},
    val onRetryLessons: () -> Unit = {},
    val onOpenResource: (SubjectResource) -> Unit = {}
)

class RecordbookControlAdapter(
    private val onRetry: () -> Unit = {},
    private val hubActions: SubjectHubActions = SubjectHubActions()
) : ListAdapter<DetailItem, RecyclerView.ViewHolder>(Diff) {

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    fun submitContent(state: RecordbookSubjectUiState.Content, onCommitted: () -> Unit = {}) {
        submitList(buildList {
            if (state.subject.isPhysicalEducation) add(DetailItem.SportOverview(state.subject, state.sport))
            else add(DetailItem.Overview(state.subject))
            if (state.controlsError != null || state.controls.isEmpty()) {
                // PE often has no control tree by design; do not add a second empty card.
                if (state.controlsError != null || !state.subject.isPhysicalEducation) {
                    add(DetailItem.Notice(state.controlsError?.messageRes()))
                }
            } else {
                add(DetailItem.Heading)
                addAll(recordbookControlOutline(state.controls).map { DetailItem.Control(it, state.subject.teacherName) })
            }
            val hub = state.hub
            if (hub.lessons != SubjectLessonsState.Hidden) {
                add(DetailItem.Section(R.string.subject_lessons_title))
                when (val lessons = hub.lessons) {
                    is SubjectLessonsState.Content -> addAll(lessons.lessons.map { DetailItem.Lesson(it) })
                    is SubjectLessonsState.Proposed -> add(DetailItem.BindingProposal(lessons.candidate))
                    is SubjectLessonsState.Ambiguous -> add(DetailItem.BindingChoice(lessons.candidates))
                    else -> add(DetailItem.LessonsMessage(lessons))
                }
            }
            if (hub.teachers.isNotEmpty()) {
                add(DetailItem.Section(R.string.subject_teachers_title))
                addAll(hub.teachers.map { DetailItem.Teacher(it) })
            }
            if (hub.resources.isNotEmpty()) {
                add(DetailItem.Section(R.string.subject_resources_title))
                addAll(hub.resources.map { DetailItem.Resource(it) })
            }
        }, onCommitted)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DetailItem.Overview -> 0
        is DetailItem.Notice -> 1
        DetailItem.Heading, is DetailItem.Section -> 2
        is DetailItem.SportOverview -> 4
        is DetailItem.Lesson -> 5
        is DetailItem.LessonsMessage -> 6
        is DetailItem.BindingProposal, is DetailItem.BindingChoice -> 7
        is DetailItem.Teacher -> 8
        is DetailItem.Resource -> 9
        is DetailItem.Control -> 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> OverviewHolder(ItemRecordbookOverviewBinding.inflate(inflater, parent, false))
            1 -> NoteHolder(ItemRecordbookNoteBinding.inflate(inflater, parent, false))
            2 -> HeadingHolder(ItemRecordbookSectionBinding.inflate(inflater, parent, false))
            4 -> RecordbookSportHolder(ItemRecordbookSportBinding.inflate(inflater, parent, false), onRetry)
            5 -> LessonHolder(ItemSubjectLessonBinding.inflate(inflater, parent, false))
            6 -> LessonsMessageHolder(ItemSubjectMessageBinding.inflate(inflater, parent, false))
            7 -> BindingHolder(ItemSubjectBindingBinding.inflate(inflater, parent, false))
            8 -> TeacherHolder(ItemSubjectTeacherBinding.inflate(inflater, parent, false))
            9 -> ResourceHolder(ItemSubjectResourceBinding.inflate(inflater, parent, false))
            else -> ControlHolder(ItemRecordbookControlBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DetailItem.Overview -> (holder as OverviewHolder).bind(item.subject)
            is DetailItem.SportOverview -> (holder as RecordbookSportHolder).bind(item.subject, item.sport)
            is DetailItem.Notice -> (holder as NoteHolder).bindNotice(item.errorRes)
            DetailItem.Heading -> (holder as HeadingHolder).binding.title.setText(R.string.recordbook_controls_title)
            is DetailItem.Section -> (holder as HeadingHolder).binding.title.setText(item.titleRes)
            is DetailItem.Control -> (holder as ControlHolder).bind(item)
            is DetailItem.Lesson -> (holder as LessonHolder).bind(item.lesson)
            is DetailItem.LessonsMessage -> (holder as LessonsMessageHolder).bind(item.state)
            is DetailItem.BindingProposal -> (holder as BindingHolder).bindProposal(item.candidate)
            is DetailItem.BindingChoice -> (holder as BindingHolder).bindChoice(item.candidates)
            is DetailItem.Teacher -> (holder as TeacherHolder).bind(item.teacher)
            is DetailItem.Resource -> (holder as ResourceHolder).bind(item.resource)
        }
    }

    private class LessonHolder(val binding: ItemSubjectLessonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(lesson: SubjectLesson) {
            val context = binding.root.context
            binding.date.text = lesson.date.format(DateTimeFormatter.ofPattern(context.getString(R.string.subject_lesson_date), Locale.forLanguageTag("ru")))
            binding.time.text = context.getString(R.string.schedule_break_range_short, lesson.start.format(TIME_FORMAT), lesson.end.format(TIME_FORMAT))
            binding.typeIndicator.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, lessonTypeColorRes(lesson.typeId)))
            binding.type.text = listOfNotNull(
                context.getString(lessonTypeNameRes(lesson.typeId)),
                lesson.room?.let { roomShortTitle(context, it) },
                lesson.building?.let { buildingShortTitle(context, it, maxLength = 10) }
            ).joinToString(" · ")
            binding.teacher.text = lesson.teacherFio
            binding.teacher.isVisible = !lesson.teacherFio.isNullOrBlank()
        }
    }

    private inner class LessonsMessageHolder(val binding: ItemSubjectMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(state: SubjectLessonsState) {
            binding.progress.isVisible = state == SubjectLessonsState.Loading
            binding.retry.isVisible = state is SubjectLessonsState.Error
            binding.retry.setOnClickListener { hubActions.onRetryLessons() }
            binding.message.setText(when (state) {
                SubjectLessonsState.Loading -> R.string.subject_lessons_loading
                is SubjectLessonsState.Error -> state.error.messageRes()
                else -> R.string.subject_lessons_unmatched
            })
        }
    }

    private inner class BindingHolder(val binding: ItemSubjectBindingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindProposal(candidate: ScheduleSubject) {
            binding.message.text = binding.root.context.getString(R.string.subject_binding_proposal, candidate.name)
            binding.choices.removeAllViews()
            binding.choices.isVisible = false
            binding.actions.isVisible = true
            binding.confirm.setOnClickListener { hubActions.onConfirmBinding(candidate.subjectId) }
            binding.reject.setOnClickListener { hubActions.onRejectProposal() }
        }

        fun bindChoice(candidates: List<ScheduleSubject>) {
            binding.message.setText(R.string.subject_binding_ambiguous)
            binding.actions.isVisible = false
            binding.choices.isVisible = true
            binding.choices.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)
            candidates.forEach { candidate ->
                val button = inflater.inflate(R.layout.item_subject_choice, binding.choices, false) as Button
                button.text = candidate.name
                button.setOnClickListener { hubActions.onConfirmBinding(candidate.subjectId) }
                binding.choices.addView(button)
            }
        }
    }

    private class TeacherHolder(val binding: ItemSubjectTeacherBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(teacher: SubjectTeacher) {
            val context = binding.root.context
            binding.avatar.setUser(teacher.name, null)
            binding.name.text = teacher.name
            binding.roles.text = teacher.roles.joinToString(" · ") { context.getString(lessonTypeNameRes(it)) }
            binding.roles.isVisible = teacher.roles.isNotEmpty()
        }
    }

    private inner class ResourceHolder(val binding: ItemSubjectResourceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(resource: SubjectResource) {
            binding.title.setText(R.string.subject_resource_lms)
            binding.root.setOnClickListener { hubActions.onOpenResource(resource) }
        }
    }

    private class OverviewHolder(val binding: ItemRecordbookOverviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(subject: RecordbookSubject) {
            val context = binding.root.context
            binding.name.text = subject.name
            binding.result.bind(subject)
            binding.assessment.text = subject.assessmentLabel(context)
            binding.meta.text = subject.teacherName
            binding.meta.isVisible = !subject.teacherName.isNullOrBlank()
        }
    }

    private inner class NoteHolder(val binding: ItemRecordbookNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindNotice(errorRes: Int?) {
            binding.title.setText(if (errorRes == null) R.string.recordbook_controls_title else R.string.common_load_error_title)
            binding.description.setText(errorRes ?: R.string.recordbook_details_empty_description)
            binding.retry.isVisible = errorRes != null
            binding.retry.setOnClickListener { onRetry() }
        }
    }

    private class HeadingHolder(val binding: ItemRecordbookSectionBinding) : RecyclerView.ViewHolder(binding.root)

    private class ControlHolder(val binding: ItemRecordbookControlBinding) : RecyclerView.ViewHolder(binding.root) {
        init { binding.name.textLocale = Locale.forLanguageTag("ru") }

        fun bind(item: DetailItem.Control) {
            val row = item.row
            val control = row.control
            val context = binding.root.context
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = (row.depth.coerceAtMost(3) * 12 * context.resources.displayMetrics.density).toInt()
            }
            binding.name.text = if (control.additional) context.getString(R.string.recordbook_additional_points) else control.name
            val progress = RecordbookProgress(control.score, control.maximum)
            val earned = progress.value?.let(::formatRecordbookNumber) ?: context.getString(R.string.recordbook_score_pending)
            binding.score.text = control.maximum?.let { context.getString(R.string.recordbook_control_score, earned, formatRecordbookNumber(it)) } ?: earned
            binding.progress.isVisible = progress.limit != null
            binding.progress.setProgressCompat(progress.progress, false)
            val minimumMet = progress.value != null && control.minimum?.takeIf { it > 0 }?.let { progress.value >= it } == true
            val completed = progress.isAvailable && progress.value!! >= progress.limit!!
            binding.progress.setIndicatorColor(if (minimumMet || completed) RecordbookSubjectStatus.PASSED.progressColor(context) else context.color.primary)
            binding.meta.text = buildList {
                control.minimum?.takeIf { it > 0 }?.let { add(context.getString(R.string.recordbook_control_minimum, formatRecordbookNumber(it))) }
                if (control.required) add(context.getString(R.string.recordbook_required))
                if (control.absent) add(context.getString(R.string.recordbook_absent))
            }.joinToString(" · ")
            binding.meta.isVisible = binding.meta.text.isNotEmpty()
            binding.details.text = listOfNotNull(control.date?.format(DATE_FORMAT),
                control.teacherName?.takeUnless { it == item.subjectTeacher }).joinToString(" · ")
            binding.details.isVisible = binding.details.text.isNotEmpty()
        }
    }

    private object Diff : DiffUtil.ItemCallback<DetailItem>() {
        override fun areItemsTheSame(old: DetailItem, new: DetailItem): Boolean = when {
            old is DetailItem.Control && new is DetailItem.Control -> old.row.control.id == new.row.control.id && old.row.control.name == new.row.control.name
            old is DetailItem.Section && new is DetailItem.Section -> old.titleRes == new.titleRes
            old is DetailItem.Lesson && new is DetailItem.Lesson -> old.lesson.pairId == new.lesson.pairId
            old is DetailItem.Teacher && new is DetailItem.Teacher -> old.teacher.name == new.teacher.name
            old is DetailItem.Resource && new is DetailItem.Resource -> old.resource.url == new.resource.url
            else -> old::class == new::class
        }
        override fun areContentsTheSame(old: DetailItem, new: DetailItem) = old == new
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    }
}
