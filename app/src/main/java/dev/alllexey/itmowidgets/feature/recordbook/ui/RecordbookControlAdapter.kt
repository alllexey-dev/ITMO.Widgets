package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemRecordbookControlBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookNoteBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookOverviewBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSectionBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSportBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControlRow
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.recordbookControlOutline
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookProgress
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface DetailItem {
    data class Overview(val subject: RecordbookSubject) : DetailItem
    data class SportOverview(val subject: RecordbookSubject, val sport: RecordbookSportState?) : DetailItem
    data object Heading : DetailItem
    data class Control(val row: RecordbookControlRow, val subjectTeacher: String?) : DetailItem
    data class Notice(val errorRes: Int?) : DetailItem
}

class RecordbookControlAdapter(private val onRetry: () -> Unit = {}) :
    ListAdapter<DetailItem, RecyclerView.ViewHolder>(Diff) {

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
        }, onCommitted)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DetailItem.Overview -> 0
        is DetailItem.Notice -> 1
        DetailItem.Heading -> 2
        is DetailItem.SportOverview -> 4
        else -> 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> OverviewHolder(ItemRecordbookOverviewBinding.inflate(inflater, parent, false))
            1 -> NoteHolder(ItemRecordbookNoteBinding.inflate(inflater, parent, false))
            2 -> HeadingHolder(ItemRecordbookSectionBinding.inflate(inflater, parent, false))
            4 -> RecordbookSportHolder(ItemRecordbookSportBinding.inflate(inflater, parent, false), onRetry)
            else -> ControlHolder(ItemRecordbookControlBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DetailItem.Overview -> (holder as OverviewHolder).bind(item.subject)
            is DetailItem.SportOverview -> (holder as RecordbookSportHolder).bind(item.subject, item.sport)
            is DetailItem.Notice -> (holder as NoteHolder).bindNotice(item.errorRes)
            DetailItem.Heading -> (holder as HeadingHolder).binding.title.setText(R.string.recordbook_controls_title)
            is DetailItem.Control -> (holder as ControlHolder).bind(item)
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
            binding.name.text = control.name
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
            else -> old::class == new::class
        }
        override fun areContentsTheSame(old: DetailItem, new: DetailItem) = old == new
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
    }
}
