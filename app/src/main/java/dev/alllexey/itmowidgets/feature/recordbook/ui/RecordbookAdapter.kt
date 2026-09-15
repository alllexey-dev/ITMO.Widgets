package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSectionBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSubjectBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSummaryBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookProgress
import java.util.Locale

sealed interface RecordbookListItem {
    data class Summary(val subjects: List<RecordbookSubject>) : RecordbookListItem
    data class Section(val titleRes: Int) : RecordbookListItem
    data class Subject(val value: RecordbookSubject, val sport: RecordbookSportState?, val barsMissing: Boolean = false) : RecordbookListItem
}

class RecordbookAdapter(private val onSubjectClick: (RecordbookSubject) -> Unit) :
    ListAdapter<RecordbookListItem, RecyclerView.ViewHolder>(Diff) {

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    fun submitData(subjects: List<RecordbookSubject>, sport: RecordbookSportState?, bars: Boolean = false, onCommitted: () -> Unit = {}) {
        val (attention, regular) = subjects.partition { it.status == RecordbookSubjectStatus.ATTENTION }
        fun item(subject: RecordbookSubject) = RecordbookListItem.Subject(subject, sport.takeIf { subject.isPhysicalEducation },
            // PE is graded outside BARS; only other unmatched subjects need the hint.
            barsMissing = bars && subject.barsJournal == null && !subject.isPhysicalEducation)
        submitList(buildList {
            if (subjects.isNotEmpty()) add(RecordbookListItem.Summary(subjects))
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
            is RecordbookListItem.Summary -> (holder as SummaryHolder).bind(item.subjects)
            is RecordbookListItem.Section -> (holder as SectionHolder).binding.title.setText(item.titleRes)
            is RecordbookListItem.Subject -> (holder as SubjectHolder).bind(item)
        }
    }

    private class SummaryHolder(val binding: ItemRecordbookSummaryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(subjects: List<RecordbookSubject>) {
            val context = binding.root.context
            binding.closedValue.text = context.getString(R.string.recordbook_summary_closed,
                subjects.count { it.status == RecordbookSubjectStatus.PASSED }, subjects.size)
            binding.pendingValue.text = context.getString(R.string.recordbook_summary_pending,
                subjects.count { it.status == RecordbookSubjectStatus.IN_PROGRESS },
                subjects.count { it.status == RecordbookSubjectStatus.ATTENTION })
            binding.completion.setProgressCompat(RecordbookProgress(
                subjects.count { it.status == RecordbookSubjectStatus.PASSED }.toDouble(), subjects.size.toDouble()
            ).progress, false)
        }
    }

    private class SectionHolder(val binding: ItemRecordbookSectionBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class SubjectHolder(val binding: ItemRecordbookSubjectBinding) : RecyclerView.ViewHolder(binding.root) {
        init { binding.name.textLocale = Locale.forLanguageTag("ru") }

        fun bind(item: RecordbookListItem.Subject) {
            val subject = item.value
            val context = binding.root.context
            binding.name.text = subject.name
            binding.meta.text = listOf(subject.assessmentLabel(context), if (item.barsMissing) context.getString(R.string.recordbook_bars_missing) else "")
                .filter(String::isNotBlank).joinToString(" · ")
            binding.result.bind(subject, item.sport)
            binding.sport.isVisible = item.sport != null
            binding.sport.text = item.sport?.compactText(context)
            binding.root.contentDescription = listOfNotNull(subject.name, subject.controlType,
                binding.result.contentDescription,
                item.sport?.takeUnless { it is RecordbookSportState.Content }?.compactText(context)).joinToString(". ")
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
