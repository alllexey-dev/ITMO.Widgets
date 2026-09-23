package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSectionBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSubjectBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSummaryBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookAttentionReason
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookProgress
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.displayedScore
import java.util.Locale

sealed interface RecordbookListItem {
    data class Summary(val passed: Int, val total: Int) : RecordbookListItem
    data class Section(val titleRes: Int) : RecordbookListItem
    data class Subject(
        val value: RecordbookSubject,
        val sport: RecordbookSportState?,
        val reason: RecordbookAttentionReason? = null,
        val barsMissing: Boolean = false
    ) : RecordbookListItem
}

class RecordbookAdapter(private val onSubjectClick: (RecordbookSubject) -> Unit) :
    ListAdapter<RecordbookListItem, RecyclerView.ViewHolder>(Diff) {

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    fun submitData(state: RecordbookUiState.Content, onCommitted: () -> Unit = {}) {
        val subjects = state.subjects
        val (attention, regular) = subjects.partition { it.entryId in state.attention }
        fun item(subject: RecordbookSubject) = RecordbookListItem.Subject(subject, state.sport.takeIf { subject.isPhysicalEducation },
            reason = state.attention[subject.entryId],
            // PE is graded outside BARS; only other unmatched subjects need the hint.
            barsMissing = state.barsApplied && subject.barsJournal == null && !subject.isPhysicalEducation)
        submitList(buildList {
            if (state.showSummary) {
                add(RecordbookListItem.Summary(subjects.count { it.status == RecordbookSubjectStatus.PASSED }, subjects.size))
            }
            if (attention.isNotEmpty()) {
                add(RecordbookListItem.Section(R.string.recordbook_attention_section))
                addAll(attention.map(::item))
            }
            if (regular.isNotEmpty()) {
                if (attention.isNotEmpty()) add(RecordbookListItem.Section(R.string.recordbook_disciplines_section))
                addAll(regular.map(::item))
            }
        }, onCommitted)
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is RecordbookListItem.Summary -> 0
        is RecordbookListItem.Section -> 1
        is RecordbookListItem.Subject -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> SummaryHolder(ItemRecordbookSummaryBinding.inflate(inflater, parent, false))
            1 -> SectionHolder(ItemRecordbookSectionBinding.inflate(inflater, parent, false))
            else -> SubjectHolder(ItemRecordbookSubjectBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RecordbookListItem.Summary -> (holder as SummaryHolder).bind(item)
            is RecordbookListItem.Section -> (holder as SectionHolder).binding.title.setText(item.titleRes)
            is RecordbookListItem.Subject -> (holder as SubjectHolder).bind(item)
        }
    }

    private class SummaryHolder(val binding: ItemRecordbookSummaryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecordbookListItem.Summary) {
            binding.closedValue.text = binding.root.context.getString(R.string.recordbook_summary_closed, item.passed, item.total)
            binding.completion.setProgressCompat(RecordbookProgress(item.passed.toDouble(), item.total.toDouble()).progress, false)
        }
    }

    private class SectionHolder(val binding: ItemRecordbookSectionBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class SubjectHolder(val binding: ItemRecordbookSubjectBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.name.textLocale = Locale.forLanguageTag("ru")
            binding.meta.textLocale = Locale.forLanguageTag("ru")
        }

        fun bind(item: RecordbookListItem.Subject) {
            val subject = item.value
            val context = binding.root.context
            binding.name.text = subject.name
            binding.meta.text = item.reason?.text(context) ?: listOf(
                subject.assessmentLabel(context),
                item.sport?.takeUnless { it is RecordbookSportState.Content }?.compactText(context).orEmpty(),
                if (item.barsMissing) context.getString(R.string.recordbook_bars_missing) else ""
            ).filter(String::isNotBlank).joinToString(" · ")
            binding.meta.setTextColor(if (item.reason != null) context.color.resolve(androidx.appcompat.R.attr.colorError)
                else context.color.resolve(com.google.android.material.R.attr.colorOnSurfaceVariant))
            // A final result is a badge; until then the points with their share of 100.
            val progress = subject.displayedScore(item.sport)
            val final = subject.absent || subject.normalizedRate != RecordbookRate.InProgress || progress.value == null
            binding.grade.isVisible = final
            binding.scoreGroup.isVisible = !final
            val result = if (final) {
                binding.grade.bindGradeBadge(subject)
                subject.displayRate(context)
            } else {
                binding.score.text = formatRecordbookNumber(progress.value!!)
                binding.progress.setIndicatorColor(when {
                    item.reason != null -> context.color.resolve(androidx.appcompat.R.attr.colorError)
                    subject.isPhysicalEducation -> ContextCompat.getColor(context, R.color.sport_score_attendance)
                    else -> subject.status.progressColor(context)
                })
                // Bind final geometry atomically; recycled rows must never animate another subject's score.
                binding.progress.setProgressCompat(progress.progress, false)
                context.getString(R.string.recordbook_points_out_of, formatRecordbookNumber(progress.value), "100")
            }
            binding.root.contentDescription = listOf(subject.name, binding.meta.text.toString(), result)
                .filter(String::isNotBlank).joinToString(". ")
            binding.root.setOnClickListener { onSubjectClick(subject) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RecordbookListItem>() {
        override fun areItemsTheSame(old: RecordbookListItem, new: RecordbookListItem): Boolean = when {
            old is RecordbookListItem.Summary && new is RecordbookListItem.Summary -> true
            old is RecordbookListItem.Section && new is RecordbookListItem.Section -> old.titleRes == new.titleRes
            old is RecordbookListItem.Subject && new is RecordbookListItem.Subject -> old.value.entryId == new.value.entryId && old.value.disciplineId == new.value.disciplineId && old.value.barsJournal == new.value.barsJournal
            else -> false
        }
        override fun areContentsTheSame(old: RecordbookListItem, new: RecordbookListItem) = old == new
    }
}
