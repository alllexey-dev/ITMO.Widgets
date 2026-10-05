package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChip
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.ui.GroupPosition
import dev.alllexey.itmowidgets.core.ui.LinkRowTrailing
import dev.alllexey.itmowidgets.core.ui.bind
import dev.alllexey.itmowidgets.core.ui.bindGroupPosition
import dev.alllexey.itmowidgets.core.ui.bindLevel
import dev.alllexey.itmowidgets.core.ui.buildingShortTitle
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.describeActions
import dev.alllexey.itmowidgets.core.ui.host
import dev.alllexey.itmowidgets.core.ui.iconRes
import dev.alllexey.itmowidgets.core.ui.label
import dev.alllexey.itmowidgets.core.ui.lessonTypeColorRes
import dev.alllexey.itmowidgets.core.ui.lessonTypeNameRes
import dev.alllexey.itmowidgets.core.ui.linkIconRes
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.roomShortTitle
import dev.alllexey.itmowidgets.core.ui.title
import dev.alllexey.itmowidgets.core.ui.tone
import dev.alllexey.itmowidgets.databinding.ItemGroupActionRowBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookControlBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookControlGroupBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookNoteBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSportBinding
import dev.alllexey.itmowidgets.databinding.ItemSectionHeadingBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectBindingBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectHeroBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectLessonBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectLinkBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectMessageBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectTeacherBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlEntry
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroup
import dev.alllexey.itmowidgets.feature.recordbook.domain.GradeStep
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookGradeScale
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookAssessmentKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControlRow
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookProgress
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SheetLinkOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacher
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.columnTitle
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.sheetCaption
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.textRes
import java.net.URI
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

sealed interface DetailItem {
    /** The result with the own sheet total, or the offer to connect one, at its bottom. */
    data class Hero(val subject: RecordbookSubject, val step: GradeStep?, val sheet: SubjectSheetState? = null) : DetailItem
    data class SportOverview(val subject: RecordbookSubject, val sport: RecordbookSportState?) : DetailItem
    /** A link of the short list under «Ссылки»: own ones carry «моя», others their votes. */
    data class Link(val link: SubjectLink, val canVote: Boolean, val position: GroupPosition) : DetailItem
    /** The MyITMO LMS page of the subject, one of the short list. */
    data class Lms(val url: String, val position: GroupPosition) : DetailItem
    /** «Все ссылки, N»: opens the links sheet. */
    data class AllLinks(val count: Int, val position: GroupPosition) : DetailItem
    /** «Добавить ссылку» in place of «Все ссылки» while the subject has no links. */
    data class AddLink(val position: GroupPosition) : DetailItem
    data class Chat(val link: SubjectLink, val position: GroupPosition) : DetailItem
    /** A heading with its own string. */
    data class Section(val titleRes: Int) : DetailItem
    /** The heading of a control group with its sum; its controls follow as their own connected group. */
    data class Group(val group: ControlGroup) : DetailItem
    /** [date] is the control's date in the academic time zone; [spaced] parts a first row from a group of controls right above it. */
    data class Control(val row: RecordbookControlRow, val date: LocalDate?, val subjectTeacher: String?, val position: GroupPosition, val spaced: Boolean = false) : DetailItem
    data class Notice(val errorRes: Int?) : DetailItem
    data class Lesson(val lesson: SubjectLesson, val position: GroupPosition) : DetailItem
    /** «Все пары, N»: the rest of the window opens in place. */
    data class AllLessons(val count: Int, val position: GroupPosition) : DetailItem
    /** Loading, empty, unmatched or failed lessons; never used for content. */
    data class LessonsMessage(val state: SubjectLessonsState) : DetailItem
    data class BindingProposal(val candidate: ScheduleSubject) : DetailItem
    data class BindingChoice(val candidates: List<ScheduleSubject>) : DetailItem
    data class Teacher(val teacher: SubjectTeacher, val level: TeacherLevel?, val position: GroupPosition) : DetailItem
}

/** Actions the subject page forwards to its view model and the link sheets. */
data class SubjectHubActions(
    val onConfirmBinding: (Long) -> Unit = {},
    val onRejectProposal: () -> Unit = {},
    val onRetryLessons: () -> Unit = {},
    val onShowAllLessons: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onLinkActions: (SubjectLink) -> Unit = {},
    val onVoteLink: (SubjectLink, Boolean) -> Unit = { _, _ -> },
    val onAllLinks: () -> Unit = {},
    val onAddLink: () -> Unit = {},
    val onOpenTeacher: (Int) -> Unit = {},
    val onOpenSheet: (String) -> Unit = {},
    val onChangeSheetTotal: () -> Unit = {},
    val onDisconnectSheet: () -> Unit = {},
    val onConnectSheet: (List<SheetLinkOption>) -> Unit = {}
)

/** Adds [rows] as one connected group: each row learns its place for the corners and gaps. */
private fun MutableList<DetailItem>.addGroup(rows: List<(GroupPosition) -> DetailItem>) =
    rows.forEachIndexed { index, row -> add(row(GroupPosition.of(index, rows.size))) }

/**
 * The whole subject page as one list: the result with the sheet total, links, chats, controls, teachers
 * and the nearest lessons. Every list section is a heading over one connected group.
 */
class SubjectHubAdapter(
    private val onRetry: () -> Unit = {},
    private val hubActions: SubjectHubActions = SubjectHubActions()
) : ListAdapter<DetailItem, RecyclerView.ViewHolder>(Diff) {

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    fun submitContent(state: RecordbookSubjectUiState.Content, onCommitted: () -> Unit = {}) {
        val hub = state.hub
        submitList(buildList {
            if (state.subject.isPhysicalEducation) add(DetailItem.SportOverview(state.subject, state.sport))
            else add(DetailItem.Hero(state.subject, state.gradeStep, hub.sheet))
            if (hub.resourceScope != null) {
                add(DetailItem.Section(R.string.links_title))
                addGroup(buildList {
                    hub.chips.visible.forEach { chip ->
                        when (chip) {
                            is SubjectLinkChip.Link -> add { position: GroupPosition -> DetailItem.Link(chip.link, hub.canVote, position) }
                            is SubjectLinkChip.Lms -> add { position: GroupPosition -> DetailItem.Lms(chip.url, position) }
                        }
                    }
                    if (hub.linkCount > 0) add { position: GroupPosition -> DetailItem.AllLinks(hub.linkCount, position) }
                    else add { position: GroupPosition -> DetailItem.AddLink(position) }
                })
            }
            if (hub.chats.isNotEmpty()) {
                add(DetailItem.Section(R.string.links_chats))
                addGroup(hub.chats.map { link -> { position: GroupPosition -> DetailItem.Chat(link, position) } })
            }
            if (state.controlsError != null || state.controls.isEmpty()) {
                // The sheet (or the offer of one) is the detail: «no details» goes, a failure to load the controls stays.
                // PE often has no control tree by design; it gets no empty card either.
                if (state.controlsError != null || (hub.sheet == null && !state.subject.isPhysicalEducation)) {
                    add(DetailItem.Notice(state.controlsError?.messageRes()))
                }
            } else {
                add(DetailItem.Section(R.string.subject_controls_title))
                addControls(state)
            }
            if (hub.teachers.isNotEmpty()) {
                add(DetailItem.Section(R.string.subject_teachers_title))
                addGroup(hub.teachers.map { teacher ->
                    { position: GroupPosition -> DetailItem.Teacher(teacher, teacher.isu?.let(hub.teacherLevels::get), position) }
                })
            }
            if (hub.lessons != SubjectLessonsState.Hidden) {
                add(DetailItem.Section(R.string.subject_lessons_title))
                when (val lessons = hub.lessons) {
                    is SubjectLessonsState.Content -> addGroup(buildList {
                        hub.visibleLessons.forEach { lesson -> add { position: GroupPosition -> DetailItem.Lesson(lesson, position) } }
                        if (hub.allLessonsCount > 0) add { position: GroupPosition -> DetailItem.AllLessons(hub.allLessonsCount, position) }
                    })
                    is SubjectLessonsState.Proposed -> add(DetailItem.BindingProposal(lessons.candidate))
                    is SubjectLessonsState.Ambiguous -> add(DetailItem.BindingChoice(lessons.candidates))
                    else -> add(DetailItem.LessonsMessage(lessons))
                }
            }
        }, onCommitted)
    }

    /** Lone controls in a row share a group; each control group gets its heading and a group of its own. */
    private fun MutableList<DetailItem>.addControls(state: RecordbookSubjectUiState.Content) {
        val teacher = state.subject.teacherName
        fun RecordbookControl.localDate() = date?.toLocalDateTime(state.timeZone)?.date
        val singles = mutableListOf<ControlEntry.Single>()
        var afterGroup = false
        fun flushSingles() {
            if (singles.isEmpty()) return
            val spaced = afterGroup
            addGroup(singles.map { entry ->
                { position: GroupPosition -> DetailItem.Control(RecordbookControlRow(entry.control, 0), entry.control.localDate(), teacher, position, spaced) }
            })
            singles.clear()
        }
        state.controlGroups.forEach { entry ->
            when (entry) {
                is ControlEntry.Single -> singles += entry
                is ControlGroup -> {
                    flushSingles()
                    add(DetailItem.Group(entry))
                    addGroup(entry.controls.map { control ->
                        { position: GroupPosition -> DetailItem.Control(RecordbookControlRow(control, 1), control.localDate(), teacher, position) }
                    })
                    afterGroup = true
                }
            }
        }
        flushSingles()
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DetailItem.Hero -> TYPE_HERO
        is DetailItem.Notice -> TYPE_NOTICE
        is DetailItem.Section -> TYPE_SECTION
        is DetailItem.Control -> TYPE_CONTROL
        is DetailItem.SportOverview -> TYPE_SPORT
        is DetailItem.Lesson -> TYPE_LESSON
        is DetailItem.LessonsMessage -> TYPE_LESSONS_MESSAGE
        is DetailItem.BindingProposal, is DetailItem.BindingChoice -> TYPE_BINDING
        is DetailItem.Teacher -> TYPE_TEACHER
        is DetailItem.Link, is DetailItem.Lms, is DetailItem.Chat -> TYPE_LINK
        is DetailItem.Group -> TYPE_GROUP
        is DetailItem.AllLinks, is DetailItem.AddLink, is DetailItem.AllLessons -> TYPE_ACTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HERO -> HeroHolder(ItemSubjectHeroBinding.inflate(inflater, parent, false))
            TYPE_NOTICE -> NoteHolder(ItemRecordbookNoteBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> HeadingHolder(ItemSectionHeadingBinding.inflate(inflater, parent, false))
            TYPE_SPORT -> RecordbookSportHolder(ItemRecordbookSportBinding.inflate(inflater, parent, false), onRetry)
            TYPE_LESSON -> LessonHolder(ItemSubjectLessonBinding.inflate(inflater, parent, false))
            TYPE_LESSONS_MESSAGE -> LessonsMessageHolder(ItemSubjectMessageBinding.inflate(inflater, parent, false))
            TYPE_BINDING -> BindingHolder(ItemSubjectBindingBinding.inflate(inflater, parent, false))
            TYPE_TEACHER -> TeacherHolder(ItemSubjectTeacherBinding.inflate(inflater, parent, false))
            TYPE_LINK -> LinkHolder(ItemSubjectLinkBinding.inflate(inflater, parent, false))
            TYPE_GROUP -> GroupHolder(ItemRecordbookControlGroupBinding.inflate(inflater, parent, false))
            TYPE_ACTION -> ActionHolder(ItemGroupActionRowBinding.inflate(inflater, parent, false))
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
            is DetailItem.Link -> (holder as LinkHolder).bindLink(item)
            is DetailItem.Lms -> (holder as LinkHolder).bindLms(item)
            is DetailItem.Chat -> (holder as LinkHolder).bindChat(item)
            is DetailItem.AllLinks -> (holder as ActionHolder).bindAllLinks(item)
            is DetailItem.AddLink -> (holder as ActionHolder).bindAddLink(item)
            is DetailItem.AllLessons -> (holder as ActionHolder).bindAllLessons(item)
            is DetailItem.Lesson -> (holder as LessonHolder).bind(item)
            is DetailItem.LessonsMessage -> (holder as LessonsMessageHolder).bind(item.state)
            is DetailItem.BindingProposal -> (holder as BindingHolder).bindProposal(item.candidate)
            is DetailItem.BindingChoice -> (holder as BindingHolder).bindChoice(item.candidates)
            is DetailItem.Teacher -> (holder as TeacherHolder).bind(item)
        }
    }

    private inner class HeroHolder(val binding: ItemSubjectHeroBinding) : RecyclerView.ViewHolder(binding.root) {
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
            binding.summary.contentDescription = listOfNotNull(
                progress.value?.let { context.getString(R.string.recordbook_points_out_of, formatRecordbookNumber(it), "100") }
                    ?: context.getString(R.string.recordbook_points_missing),
                if (final) subject.displayRate(context) else null,
                hint
            ).joinToString(". ")
            bindSheet(item.sheet)
        }

        private fun bindSheet(state: SubjectSheetState?) {
            binding.sheetDivider.isVisible = state is SubjectSheetState.Connected
            binding.sheet.root.isVisible = state is SubjectSheetState.Connected
            binding.sheetHint.isVisible = state is SubjectSheetState.Hint
            when (state) {
                is SubjectSheetState.Connected -> bindConnected(state)
                is SubjectSheetState.Hint -> binding.sheetHint.setOnClickListener { hubActions.onConnectSheet(state.links) }
                null -> Unit
            }
        }

        /** Value, «путь, лист «Лист»» and when it was read; the row opens the tab, `⋮` holds the rest. */
        private fun bindConnected(state: SubjectSheetState.Connected) {
            val row = binding.sheet
            val context = row.root.context
            val score = state.score
            row.value.text = score.value ?: context.getString(R.string.recordbook_score_pending)
            row.caption.text = context.sheetCaption(score.tabName, context.columnTitle(score.column.headerPath, score.column.index))
            val updated = state.updatedAt?.let { at ->
                if (at.date == state.today) context.getString(R.string.sheet_scores_updated_time, at.time.format(DateTexts.TIME))
                else context.getString(R.string.sheet_scores_updated_date, at.date.format(DateTexts.DAY_MONTH))
            }
            val failure = score.status.textRes()?.let(context::getString)
            // Offline keeps the stored value and says only that; the time of a stale value would wrap on narrow screens.
            row.status.text = if (score.status == SheetStatus.OK) updated else failure
            row.status.isVisible = !row.status.text.isNullOrEmpty()
            row.status.setTextColor(
                if (score.status == SheetStatus.OK || score.status == SheetStatus.NETWORK) {
                    context.color.resolve(com.google.android.material.R.attr.colorOnSurfaceVariant)
                } else {
                    context.color.resolve(androidx.appcompat.R.attr.colorError)
                }
            )
            row.root.contentDescription = listOf(row.value.text, row.caption.text, row.status.text)
                .filter { !it.isNullOrEmpty() }.joinToString(", ")
            row.root.setOnClickListener { hubActions.onOpenSheet(score.tabUrl) }
            row.menu.setOnClickListener { button ->
                val popup = PopupMenu(button.context, button)
                popup.menu.add(0, MENU_OPEN, 0, R.string.sheet_scores_open)
                popup.menu.add(0, MENU_TOTAL, 1, R.string.sheet_scores_change_total)
                popup.menu.add(0, MENU_DISCONNECT, 2, R.string.sheet_scores_disconnect)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_OPEN -> hubActions.onOpenSheet(score.tabUrl)
                        MENU_TOTAL -> hubActions.onChangeSheetTotal()
                        MENU_DISCONNECT -> hubActions.onDisconnectSheet()
                    }
                    true
                }
                popup.show()
            }
        }
    }

    /** Links, the LMS page and chats share one row; a tap opens, a long press opens a link's actions. */
    private inner class LinkHolder(val binding: ItemSubjectLinkBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindLink(item: DetailItem.Link) {
            val link = item.link
            val trailing = if (link.isMine) LinkRowTrailing.Own
                else LinkRowTrailing.Votes(link, item.canVote) { up -> hubActions.onVoteLink(link, up) }
            binding.bind(link.category.iconRes(), link.title ?: link.category.title().resolve(binding.root.context), link.host(), trailing)
            bindLinkActions(link, item.position)
        }

        fun bindLms(item: DetailItem.Lms) {
            val host = runCatching { URI(item.url).host }.getOrNull()?.removePrefix("www.")
            binding.bind(LinkCategory.MATERIALS.iconRes(), binding.root.context.getString(R.string.subject_link_lms), host, LinkRowTrailing.None)
            binding.root.bindGroupPosition(item.position)
            binding.root.setOnClickListener { hubActions.onOpenLink(item.url) }
            binding.root.setOnLongClickListener(null)
            binding.root.isLongClickable = false
            ViewCompat.removeAccessibilityAction(binding.root, AccessibilityActionCompat.ACTION_LONG_CLICK.id)
        }

        fun bindChat(item: DetailItem.Chat) {
            val link = item.link
            val context = binding.root.context
            binding.bind(linkIconRes(link.category, link.url), link.title ?: link.host(),
                link.visibility.label(link.audienceLabel).resolve(context),
                if (link.isMine) LinkRowTrailing.Own else LinkRowTrailing.None)
            bindLinkActions(link, item.position)
        }

        private fun bindLinkActions(link: SubjectLink, position: GroupPosition) {
            binding.root.bindGroupPosition(position)
            binding.root.setOnClickListener { hubActions.onOpenLink(link.url) }
            binding.root.setOnLongClickListener { hubActions.onLinkActions(link); true }
            binding.describeActions()
        }
    }

    /** The row that ends a group and leads further: all links, adding the first one, all lessons. */
    private inner class ActionHolder(val binding: ItemGroupActionRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindAllLinks(item: DetailItem.AllLinks) {
            // No symbol of its own, but the text stays in line with the link titles above it.
            bind(binding.root.context.getString(R.string.links_all_count, item.count), icon = null, keepIconSpace = true,
                trailing = R.drawable.ic_chevron_right, position = item.position) { hubActions.onAllLinks() }
        }

        fun bindAddLink(item: DetailItem.AddLink) =
            bind(binding.root.context.getString(R.string.links_add), icon = R.drawable.ic_add, keepIconSpace = true,
                trailing = null, position = item.position) { hubActions.onAddLink() }

        fun bindAllLessons(item: DetailItem.AllLessons) =
            bind(binding.root.context.getString(R.string.subject_lessons_all, item.count), icon = null, keepIconSpace = false,
                trailing = R.drawable.ic_expand_more, position = item.position) { hubActions.onShowAllLessons() }

        private fun bind(text: String, icon: Int?, keepIconSpace: Boolean, trailing: Int?, position: GroupPosition, onClick: () -> Unit) {
            binding.title.text = text
            binding.icon.visibility = when {
                icon != null -> View.VISIBLE
                keepIconSpace -> View.INVISIBLE
                else -> View.GONE
            }
            icon?.let(binding.icon::setImageResource)
            binding.chevron.isVisible = trailing != null
            trailing?.let(binding.chevron::setImageResource)
            binding.root.bindGroupPosition(position)
            binding.root.setOnClickListener { onClick() }
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

    private class LessonHolder(val binding: ItemSubjectLessonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DetailItem.Lesson) {
            val lesson = item.lesson
            val context = binding.root.context
            binding.root.bindGroupPosition(item.position)
            binding.date.text = lesson.date.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH)
            binding.time.text = context.getString(R.string.schedule_break_range_short, lesson.start.format(DateTexts.TIME), lesson.end.format(DateTexts.TIME))
            binding.typeIndicator.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, lessonTypeColorRes(lesson.typeId)))
            binding.type.text = listOfNotNull(
                context.getString(lessonTypeNameRes(lesson.typeId)),
                lesson.room?.let { roomShortTitle(context, it) },
                lesson.building?.let { buildingShortTitle(context, it, maxLength = 10) }
            ).joinToString(", ")
            binding.teacher.text = lesson.teacherFio
            binding.teacher.isVisible = !lesson.teacherFio.isNullOrBlank()
        }
    }

    private inner class LessonsMessageHolder(val binding: ItemSubjectMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(state: SubjectLessonsState) {
            binding.root.bindGroupPosition(GroupPosition.SINGLE)
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

    private inner class TeacherHolder(val binding: ItemSubjectTeacherBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DetailItem.Teacher) {
            val teacher = item.teacher
            val level = item.level
            val context = binding.root.context
            binding.avatar.setUser(teacher.name, null)
            binding.name.text = teacher.name
            // A teacher with an ISU keeps the dot's place, so a tone arriving later does not move the chevron.
            binding.levelDot.bindLevel(level, reserve = UserScreenArgs.profileIsu(teacher.isu) != null)
            binding.name.contentDescription = level?.let { "${teacher.name}, ${it.tone().description(context).lowercase()}" }
            binding.roles.text = teacher.roles.joinToString(", ") { context.getString(lessonTypeNameRes(it)) }
            binding.roles.isVisible = teacher.roles.isNotEmpty()
            val isu = UserScreenArgs.profileIsu(teacher.isu)
            binding.root.setOnClickListener(if (isu != null) { _ -> hubActions.onOpenTeacher(isu) } else null)
            binding.root.isClickable = isu != null
            binding.root.isFocusable = isu != null
            binding.trailing.isVisible = isu != null
            // The ripple of the group surface shows only for a clickable row.
            binding.root.bindGroupPosition(item.position)
            if (isu != null) {
                ViewCompat.replaceAccessibilityAction(binding.root, AccessibilityActionCompat.ACTION_CLICK,
                    context.getString(R.string.teacher_open_profile), null)
            } else {
                ViewCompat.removeAccessibilityAction(binding.root, AccessibilityActionCompat.ACTION_CLICK.id)
            }
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

    private class HeadingHolder(val binding: ItemSectionHeadingBinding) : RecyclerView.ViewHolder(binding.root)

    private class ControlHolder(val binding: ItemRecordbookControlBinding) : RecyclerView.ViewHolder(binding.root) {
        init { binding.name.textLocale = Locale.forLanguageTag("ru") }

        fun bind(item: DetailItem.Control) {
            val row = item.row
            val control = row.control
            val context = binding.root.context
            binding.root.bindGroupPosition(item.position, spaceBefore =
                if (item.spaced) context.resources.getDimensionPixelSize(R.dimen.design_spacing_content) else 0)
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
            }.joinToString(", ")
            binding.meta.isVisible = binding.meta.text.isNotEmpty()
            binding.meta.setTextColor(context.color.resolve(androidx.appcompat.R.attr.colorError))
            binding.details.text = listOfNotNull(item.date?.format(DateTexts.DAY_MONTH_YEAR),
                control.teacherName?.takeUnless { it == item.subjectTeacher }).joinToString(", ")
            binding.details.isVisible = binding.details.text.isNotEmpty()
        }
    }

    private object Diff : DiffUtil.ItemCallback<DetailItem>() {
        override fun areItemsTheSame(old: DetailItem, new: DetailItem): Boolean = when {
            old is DetailItem.Control && new is DetailItem.Control -> old.row.control.id == new.row.control.id && old.row.control.name == new.row.control.name
            old is DetailItem.Section && new is DetailItem.Section -> old.titleRes == new.titleRes
            old is DetailItem.Group && new is DetailItem.Group -> old.group.controls.firstOrNull()?.id == new.group.controls.firstOrNull()?.id
            old is DetailItem.Chat && new is DetailItem.Chat -> old.link.id == new.link.id
            old is DetailItem.Link && new is DetailItem.Link -> old.link.id == new.link.id
            old is DetailItem.Lesson && new is DetailItem.Lesson -> old.lesson.pairId == new.lesson.pairId
            old is DetailItem.Teacher && new is DetailItem.Teacher -> old.teacher.name == new.teacher.name
            else -> old::class == new::class
        }
        override fun areContentsTheSame(old: DetailItem, new: DetailItem) = old == new
    }

    private companion object {
        const val MENU_OPEN = 1
        const val MENU_TOTAL = 2
        const val MENU_DISCONNECT = 3
        const val TYPE_HERO = 0
        const val TYPE_NOTICE = 1
        const val TYPE_SECTION = 2
        const val TYPE_CONTROL = 3
        const val TYPE_SPORT = 4
        const val TYPE_LESSON = 5
        const val TYPE_LESSONS_MESSAGE = 6
        const val TYPE_BINDING = 7
        const val TYPE_TEACHER = 8
        const val TYPE_LINK = 9
        const val TYPE_GROUP = 10
        const val TYPE_ACTION = 11
    }
}
