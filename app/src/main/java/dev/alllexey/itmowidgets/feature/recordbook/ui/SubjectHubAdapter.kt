package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChip
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChips
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.ui.buildingShortTitle
import dev.alllexey.itmowidgets.core.ui.iconRes
import dev.alllexey.itmowidgets.core.ui.label
import dev.alllexey.itmowidgets.core.ui.lessonTypeColorRes
import dev.alllexey.itmowidgets.core.ui.lessonTypeNameRes
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.roomShortTitle
import dev.alllexey.itmowidgets.core.ui.title
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemRecordbookControlBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookControlGroupBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookNoteBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSectionBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSportBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectBindingBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectChatBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectHeroBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectLessonBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectLinkChipsBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectMessageBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectMoreBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectTeacherBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlEntry
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroup
import dev.alllexey.itmowidgets.feature.recordbook.domain.GradeStep
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookGradeScale
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookAssessmentKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControlRow
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookProgress
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacher
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface DetailItem {
    data class Hero(val subject: RecordbookSubject, val step: GradeStep?) : DetailItem
    data class SportOverview(val subject: RecordbookSubject, val sport: RecordbookSportState?) : DetailItem
    data class LinkChips(val chips: SubjectLinkChips) : DetailItem
    data class Chat(val link: SubjectLink) : DetailItem
    /** A heading with its own string. */
    data class Section(val titleRes: Int) : DetailItem
    data class Group(val group: ControlGroup) : DetailItem
    data class Control(val row: RecordbookControlRow, val subjectTeacher: String?) : DetailItem
    data class Notice(val errorRes: Int?) : DetailItem
    data class Lesson(val lesson: SubjectLesson) : DetailItem
    /** «Все пары · N»: the rest of the window opens in place. */
    data class AllLessons(val count: Int) : DetailItem
    /** Loading, empty, unmatched or failed lessons; never used for content. */
    data class LessonsMessage(val state: SubjectLessonsState) : DetailItem
    data class BindingProposal(val candidate: ScheduleSubject) : DetailItem
    data class BindingChoice(val candidates: List<ScheduleSubject>) : DetailItem
    data class Teacher(val teacher: SubjectTeacher) : DetailItem
}

/** Actions the subject page forwards to its view model and the link sheets. */
data class SubjectHubActions(
    val onConfirmBinding: (Long) -> Unit = {},
    val onRejectProposal: () -> Unit = {},
    val onRetryLessons: () -> Unit = {},
    val onShowAllLessons: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onLinkActions: (SubjectLink) -> Unit = {},
    val onAllLinks: () -> Unit = {},
    val onAddLink: () -> Unit = {}
)

/** The whole subject page as one list: result, links, chats, scores, teachers and the nearest lessons. */
class SubjectHubAdapter(
    private val onRetry: () -> Unit = {},
    private val hubActions: SubjectHubActions = SubjectHubActions()
) : ListAdapter<DetailItem, RecyclerView.ViewHolder>(Diff) {

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    fun submitContent(state: RecordbookSubjectUiState.Content, onCommitted: () -> Unit = {}) {
        val hub = state.hub
        submitList(buildList {
            if (state.subject.isPhysicalEducation) add(DetailItem.SportOverview(state.subject, state.sport))
            else add(DetailItem.Hero(state.subject, state.gradeStep))
            if (hub.resourceScope != null) add(DetailItem.LinkChips(hub.chips))
            if (hub.chats.isNotEmpty()) {
                add(DetailItem.Section(R.string.links_chats))
                addAll(hub.chats.map(DetailItem::Chat))
            }
            if (state.controlsError != null || state.controls.isEmpty()) {
                // PE often has no control tree by design; do not add a second empty card.
                if (state.controlsError != null || !state.subject.isPhysicalEducation) {
                    add(DetailItem.Notice(state.controlsError?.messageRes()))
                }
            } else {
                add(DetailItem.Section(R.string.subject_scores_title))
                state.controlGroups.forEach { entry ->
                    when (entry) {
                        is ControlEntry.Single -> add(DetailItem.Control(RecordbookControlRow(entry.control, 0), state.subject.teacherName))
                        is ControlGroup -> {
                            add(DetailItem.Group(entry))
                            addAll(entry.controls.map { DetailItem.Control(RecordbookControlRow(it, 1), state.subject.teacherName) })
                        }
                    }
                }
            }
            if (hub.teachers.isNotEmpty()) {
                add(DetailItem.Section(R.string.subject_teachers_title))
                addAll(hub.teachers.map(DetailItem::Teacher))
            }
            if (hub.lessons != SubjectLessonsState.Hidden) {
                add(DetailItem.Section(R.string.subject_lessons_title))
                when (val lessons = hub.lessons) {
                    is SubjectLessonsState.Content -> {
                        addAll(hub.visibleLessons.map(DetailItem::Lesson))
                        if (hub.allLessonsCount > 0) add(DetailItem.AllLessons(hub.allLessonsCount))
                    }
                    is SubjectLessonsState.Proposed -> add(DetailItem.BindingProposal(lessons.candidate))
                    is SubjectLessonsState.Ambiguous -> add(DetailItem.BindingChoice(lessons.candidates))
                    else -> add(DetailItem.LessonsMessage(lessons))
                }
            }
        }, onCommitted)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DetailItem.Hero -> 0
        is DetailItem.Notice -> 1
        is DetailItem.Section -> 2
        is DetailItem.Control -> 3
        is DetailItem.SportOverview -> 4
        is DetailItem.Lesson -> 5
        is DetailItem.LessonsMessage -> 6
        is DetailItem.BindingProposal, is DetailItem.BindingChoice -> 7
        is DetailItem.Teacher -> 8
        is DetailItem.LinkChips -> 9
        is DetailItem.Chat -> 10
        is DetailItem.Group -> 11
        is DetailItem.AllLessons -> 12
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeroHolder(ItemSubjectHeroBinding.inflate(inflater, parent, false))
            1 -> NoteHolder(ItemRecordbookNoteBinding.inflate(inflater, parent, false))
            2 -> HeadingHolder(ItemRecordbookSectionBinding.inflate(inflater, parent, false))
            4 -> RecordbookSportHolder(ItemRecordbookSportBinding.inflate(inflater, parent, false), onRetry)
            5 -> LessonHolder(ItemSubjectLessonBinding.inflate(inflater, parent, false))
            6 -> LessonsMessageHolder(ItemSubjectMessageBinding.inflate(inflater, parent, false))
            7 -> BindingHolder(ItemSubjectBindingBinding.inflate(inflater, parent, false))
            8 -> TeacherHolder(ItemSubjectTeacherBinding.inflate(inflater, parent, false))
            9 -> ChipsHolder(ItemSubjectLinkChipsBinding.inflate(inflater, parent, false))
            10 -> ChatHolder(ItemSubjectChatBinding.inflate(inflater, parent, false))
            11 -> GroupHolder(ItemRecordbookControlGroupBinding.inflate(inflater, parent, false))
            12 -> AllLessonsHolder(ItemSubjectMoreBinding.inflate(inflater, parent, false))
            else -> ControlHolder(ItemRecordbookControlBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DetailItem.Hero -> (holder as HeroHolder).bind(item)
            is DetailItem.SportOverview -> (holder as RecordbookSportHolder).bind(item.subject, item.sport)
            is DetailItem.Notice -> (holder as NoteHolder).bindNotice(item.errorRes)
            is DetailItem.Section -> (holder as HeadingHolder).binding.title.setText(item.titleRes)
            is DetailItem.Group -> (holder as GroupHolder).bind(item.group)
            is DetailItem.Control -> (holder as ControlHolder).bind(item)
            is DetailItem.LinkChips -> (holder as ChipsHolder).bind(item.chips)
            is DetailItem.Chat -> (holder as ChatHolder).bind(item.link)
            is DetailItem.Lesson -> (holder as LessonHolder).bind(item.lesson)
            is DetailItem.AllLessons -> (holder as AllLessonsHolder).bind(item.count)
            is DetailItem.LessonsMessage -> (holder as LessonsMessageHolder).bind(item.state)
            is DetailItem.BindingProposal -> (holder as BindingHolder).bindProposal(item.candidate)
            is DetailItem.BindingChoice -> (holder as BindingHolder).bindChoice(item.candidates)
            is DetailItem.Teacher -> (holder as TeacherHolder).bind(item.teacher)
        }
    }

    private class HeroHolder(val binding: ItemSubjectHeroBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DetailItem.Hero) {
            val subject = item.subject
            val context = binding.root.context
            val progress = RecordbookProgress(subject.score)
            binding.points.text = progress.value?.let(::formatRecordbookNumber) ?: context.getString(R.string.recordbook_score_pending)
            val final = subject.absent || subject.normalizedRate != RecordbookRate.InProgress
            binding.grade.isVisible = final
            if (final) binding.grade.bindGradeBadge(subject)
            val credit = subject.assessmentKind == RecordbookAssessmentKind.CREDIT
            val ticks = if (credit) {
                listOf(GradeScaleView.Tick(RecordbookGradeScale.CREDIT_SCORE, context.getString(R.string.subject_credit_mark)))
            } else {
                RecordbookGradeScale.lowestScores.map { (code, score) -> GradeScaleView.Tick(score, code.takeLast(1)) }
            }
            binding.scale.bind(progress.value, ticks, subject.status.progressColor(context))
            val hint = item.step?.text(context)
            binding.hint.text = hint
            binding.hint.isVisible = hint != null
            (binding.root.getChildAt(0)).contentDescription = listOfNotNull(
                progress.value?.let { context.getString(R.string.recordbook_points_out_of, formatRecordbookNumber(it), "100") }
                    ?: context.getString(R.string.recordbook_points_missing),
                if (final) subject.displayRate(context) else null,
                hint
            ).joinToString(". ")
        }
    }

    private inner class ChipsHolder(val binding: ItemSubjectLinkChipsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chips: SubjectLinkChips) {
            val group = binding.chips
            val context = group.context
            val inflater = LayoutInflater.from(context)
            group.removeAllViews()
            fun chip(text: CharSequence, icon: Int?): Chip =
                (inflater.inflate(R.layout.item_subject_link_chip, group, false) as Chip).apply {
                    this.text = text
                    isChipIconVisible = icon != null
                    icon?.let(::setChipIconResource)
                    group.addView(this)
                }
            chips.visible.forEach { item ->
                when (item) {
                    is SubjectLinkChip.Link -> chip(item.link.title ?: item.link.category.title().resolve(context), item.category.iconRes()).apply {
                        setOnClickListener { hubActions.onOpenLink(item.link.url) }
                        setOnLongClickListener { hubActions.onLinkActions(item.link); true }
                    }
                    is SubjectLinkChip.Lms -> chip(context.getString(R.string.subject_link_lms), item.category.iconRes()).apply {
                        setOnClickListener { hubActions.onOpenLink(item.url) }
                    }
                }
            }
            if (chips.moreCount > 0) {
                chip(context.getString(R.string.links_more, chips.moreCount), null).setOnClickListener { hubActions.onAllLinks() }
            }
            // Alone, the add chip says what it does; next to links it is an icon with a description.
            val alone = group.isEmpty()
            chip(if (alone) context.getString(R.string.links_add) else "", R.drawable.ic_add).apply {
                contentDescription = context.getString(R.string.links_add)
                if (!alone) {
                    textStartPadding = 0f
                    textEndPadding = 0f
                    chipEndPadding = chipStartPadding
                }
                setOnClickListener { hubActions.onAddLink() }
            }
        }
    }

    private inner class ChatHolder(val binding: ItemSubjectChatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(link: SubjectLink) {
            val context = binding.root.context
            binding.title.text = link.title ?: link.url.toUri().host ?: link.url
            binding.caption.text = link.visibility.label(link.audienceLabel?.let { LinkAudience(link.visibility, it) }).resolve(context)
            binding.root.setOnClickListener { hubActions.onOpenLink(link.url) }
            binding.root.setOnLongClickListener { hubActions.onLinkActions(link); true }
        }
    }

    private class GroupHolder(val binding: ItemRecordbookControlGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(group: ControlGroup) {
            val context = binding.root.context
            val title = group.displayTitle(context)
            binding.title.text = title
            val earned = group.score?.let(::formatRecordbookNumber) ?: context.getString(R.string.recordbook_score_pending)
            binding.score.text = group.maximum?.let { context.getString(R.string.recordbook_control_score, earned, formatRecordbookNumber(it)) } ?: earned
            val below = group.belowMinimum.isNotEmpty()
            binding.belowMinimum.isVisible = below
            if (below) {
                binding.belowMinimum.setTextColor(context.color.resolve(com.google.android.material.R.attr.colorOnErrorContainer))
                binding.belowMinimum.background?.mutate()?.setTint(context.color.resolve(com.google.android.material.R.attr.colorErrorContainer))
            }
            binding.root.contentDescription = listOfNotNull(title, binding.score.text,
                context.getString(R.string.recordbook_group_below_minimum).takeIf { below }).joinToString(". ")
        }
    }

    private inner class AllLessonsHolder(val binding: ItemSubjectMoreBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(count: Int) {
            binding.more.text = binding.root.context.getString(R.string.subject_lessons_all, count)
            binding.more.setOnClickListener { hubActions.onShowAllLessons() }
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

    private inner class NoteHolder(val binding: ItemRecordbookNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindNotice(errorRes: Int?) {
            binding.title.setText(if (errorRes == null) R.string.subject_scores_title else R.string.common_load_error_title)
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
            val minimum = control.minimum?.takeIf { it > 0 }
            val minimumMet = progress.value != null && minimum?.let { progress.value >= it } == true
            val belowMinimum = progress.value != null && minimum != null && progress.value < minimum
            val completed = progress.isAvailable && progress.value!! >= progress.limit!!
            binding.progress.setIndicatorColor(when {
                belowMinimum -> context.color.resolve(androidx.appcompat.R.attr.colorError)
                minimumMet || completed -> RecordbookSubjectStatus.PASSED.progressColor(context)
                else -> context.color.primary
            })
            // Requirements are shown only when they are broken; a met minimum is noise.
            binding.meta.text = buildList {
                if (belowMinimum) add(context.getString(R.string.recordbook_control_minimum, formatRecordbookNumber(minimum!!)))
                if (control.absent) add(context.getString(R.string.recordbook_absent))
            }.joinToString(" · ")
            binding.meta.isVisible = binding.meta.text.isNotEmpty()
            binding.meta.setTextColor(context.color.resolve(androidx.appcompat.R.attr.colorError))
            binding.details.text = listOfNotNull(control.date?.format(DATE_FORMAT),
                control.teacherName?.takeUnless { it == item.subjectTeacher }).joinToString(" · ")
            binding.details.isVisible = binding.details.text.isNotEmpty()
        }
    }

    private object Diff : DiffUtil.ItemCallback<DetailItem>() {
        override fun areItemsTheSame(old: DetailItem, new: DetailItem): Boolean = when {
            old is DetailItem.Control && new is DetailItem.Control -> old.row.control.id == new.row.control.id && old.row.control.name == new.row.control.name
            old is DetailItem.Section && new is DetailItem.Section -> old.titleRes == new.titleRes
            old is DetailItem.Group && new is DetailItem.Group -> old.group.controls.firstOrNull()?.id == new.group.controls.firstOrNull()?.id
            old is DetailItem.Chat && new is DetailItem.Chat -> old.link.id == new.link.id
            old is DetailItem.Lesson && new is DetailItem.Lesson -> old.lesson.pairId == new.lesson.pairId
            old is DetailItem.Teacher && new is DetailItem.Teacher -> old.teacher.name == new.teacher.name
            else -> old::class == new::class
        }
        override fun areContentsTheSame(old: DetailItem, new: DetailItem) = old == new
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    }
}
