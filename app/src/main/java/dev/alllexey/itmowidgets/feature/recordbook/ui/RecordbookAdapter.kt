package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSectionBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSubjectBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSummaryBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import java.text.NumberFormat

sealed interface RecordbookListItem {
    data class Summary(val subjects: List<RecordbookSubject>) : RecordbookListItem
    data class Section(val titleRes: Int) : RecordbookListItem
    data class Subject(val value: RecordbookSubject) : RecordbookListItem
}

class RecordbookAdapter(
    private val onSubjectClick: (RecordbookSubject) -> Unit
) : ListAdapter<RecordbookListItem, RecyclerView.ViewHolder>(Diff) {

    fun submitData(
        allSubjects: List<RecordbookSubject>,
        visibleSubjects: List<RecordbookSubject>
    ) {
        val attention = visibleSubjects.filter {
            it.status == RecordbookSubjectStatus.ATTENTION
        }
        val regular = visibleSubjects.filterNot {
            it.status == RecordbookSubjectStatus.ATTENTION
        }
        val items = buildList {
            add(RecordbookListItem.Summary(allSubjects))
            if (attention.isNotEmpty()) {
                add(RecordbookListItem.Section(R.string.recordbook_attention_section))
                addAll(attention.map(RecordbookListItem::Subject))
            }
            if (regular.isNotEmpty()) {
                add(RecordbookListItem.Section(R.string.recordbook_disciplines_section))
                addAll(regular.map(RecordbookListItem::Subject))
            }
        }
        submitList(items)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is RecordbookListItem.Summary -> VIEW_TYPE_SUMMARY
        is RecordbookListItem.Section -> VIEW_TYPE_SECTION
        is RecordbookListItem.Subject -> VIEW_TYPE_SUBJECT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SUMMARY -> SummaryViewHolder(
                ItemRecordbookSummaryBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_SECTION -> SectionViewHolder(
                ItemRecordbookSectionBinding.inflate(inflater, parent, false)
            )
            else -> SubjectViewHolder(
                ItemRecordbookSubjectBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RecordbookListItem.Summary -> (holder as SummaryViewHolder).bind(item.subjects)
            is RecordbookListItem.Section -> (holder as SectionViewHolder).bind(item.titleRes)
            is RecordbookListItem.Subject -> (holder as SubjectViewHolder).bind(item.value)
        }
    }

    private class SummaryViewHolder(
        private val binding: ItemRecordbookSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(subjects: List<RecordbookSubject>) {
            val closed = subjects.count { it.status == RecordbookSubjectStatus.PASSED }
            binding.closedValue.text = binding.root.context.getString(
                R.string.recordbook_closed_value,
                closed,
                subjects.size
            )
            val scores = subjects.mapNotNull(RecordbookSubject::score)
            binding.averageValue.text = scores.takeIf { it.isNotEmpty() }
                ?.average()
                ?.let(::formatNumber)
                ?: "—"
            binding.progress.max = subjects.size.coerceAtLeast(1)
            binding.progress.trackStopIndicatorSize = 0
            binding.progress.setProgressCompat(closed, true)
        }
    }

    private class SectionViewHolder(
        private val binding: ItemRecordbookSectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(titleRes: Int) {
            binding.title.setText(titleRes)
        }
    }

    private inner class SubjectViewHolder(
        private val binding: ItemRecordbookSubjectBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(subject: RecordbookSubject) {
            val colors = binding.root.context.color
            val accentColor = when (subject.status) {
                RecordbookSubjectStatus.PASSED -> colors.primary
                RecordbookSubjectStatus.ATTENTION -> colors.error
                RecordbookSubjectStatus.IN_PROGRESS -> colors.primary
            }
            binding.name.text = subject.name.trim()
            binding.type.text = subject.controlType.trim()
            binding.rate.text = subject.displayRate(binding.root.context)
            binding.rate.setTextColor(
                if (subject.status == RecordbookSubjectStatus.ATTENTION) {
                    colors.error
                } else {
                    colors.onSurface
                }
            )
            binding.score.text = subject.score?.let { score ->
                binding.root.context.getString(
                    R.string.recordbook_score_value,
                    formatNumber(score)
                )
            } ?: binding.root.context.getString(R.string.recordbook_no_score)
            binding.root.strokeColor = if (
                subject.status == RecordbookSubjectStatus.ATTENTION
            ) {
                colors.error
            } else {
                colors.outlineVariant
            }
            binding.progress.setIndicatorColor(accentColor)
            binding.progress.trackColor = colors.surfaceVariant
            binding.progress.trackStopIndicatorSize = 0
            binding.progress.progress = 0
            binding.progress.setProgressCompat(
                subject.score?.toInt()?.coerceIn(0, 100) ?: 0,
                true
            )
            binding.root.setOnClickListener { onSubjectClick(subject) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RecordbookListItem>() {
        override fun areItemsTheSame(
            oldItem: RecordbookListItem,
            newItem: RecordbookListItem
        ): Boolean = when {
            oldItem is RecordbookListItem.Summary && newItem is RecordbookListItem.Summary -> true
            oldItem is RecordbookListItem.Section && newItem is RecordbookListItem.Section -> {
                oldItem.titleRes == newItem.titleRes
            }
            oldItem is RecordbookListItem.Subject && newItem is RecordbookListItem.Subject -> {
                oldItem.value.entryId == newItem.value.entryId
            }
            else -> false
        }

        override fun areContentsTheSame(
            oldItem: RecordbookListItem,
            newItem: RecordbookListItem
        ): Boolean = oldItem == newItem
    }

    private companion object {
        const val VIEW_TYPE_SUMMARY = 0
        const val VIEW_TYPE_SECTION = 1
        const val VIEW_TYPE_SUBJECT = 2

        fun formatNumber(value: Double): String {
            return NumberFormat.getNumberInstance().apply {
                maximumFractionDigits = 1
            }.format(value)
        }
    }
}
