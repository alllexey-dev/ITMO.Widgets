package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.ItemRecordbookControlBinding
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSectionBinding
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookControl
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface ControlListItem {
    data class Section(val titleRes: Int) : ControlListItem
    data class Control(val value: RecordbookControl) : ControlListItem
}

class RecordbookControlAdapter :
    ListAdapter<ControlListItem, RecyclerView.ViewHolder>(Diff) {

    fun submitControls(controls: List<RecordbookControl>) {
        val grouped = controls.groupBy(::category)
        val order = listOf(
            ControlCategory.FINAL,
            ControlCategory.HOMEWORK,
            ControlCategory.TESTS,
            ControlCategory.OTHER
        )
        submitList(buildList {
            order.forEach { category ->
                val values = grouped[category].orEmpty()
                if (values.isNotEmpty()) {
                    add(ControlListItem.Section(category.titleRes))
                    addAll(values.map(ControlListItem::Control))
                }
            }
        })
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ControlListItem.Section -> VIEW_TYPE_SECTION
        is ControlListItem.Control -> VIEW_TYPE_CONTROL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SECTION) {
            SectionViewHolder(ItemRecordbookSectionBinding.inflate(inflater, parent, false))
        } else {
            ControlViewHolder(ItemRecordbookControlBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ControlListItem.Section -> (holder as SectionViewHolder).bind(item.titleRes)
            is ControlListItem.Control -> (holder as ControlViewHolder).bind(item.value)
        }
    }

    private class SectionViewHolder(
        private val binding: ItemRecordbookSectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(titleRes: Int) {
            binding.title.setText(titleRes)
        }
    }

    private class ControlViewHolder(
        private val binding: ItemRecordbookControlBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(control: RecordbookControl) {
            binding.name.text = control.name.trim()
            binding.score.text = control.score?.let { score ->
                binding.root.context.getString(
                    R.string.recordbook_control_score,
                    formatNumber(score),
                    formatNumber(control.maximum)
                )
            } ?: "—"
            binding.range.isVisible = control.minimum > 0
            binding.range.text = if (control.minimum > 0) {
                binding.root.context.getString(
                    R.string.recordbook_control_minimum,
                    formatNumber(control.minimum)
                )
            } else {
                null
            }
            val meta = buildList {
                control.date?.let { add(it.format(DATE_FORMAT)) }
                if (control.required) {
                    add(binding.root.context.getString(R.string.recordbook_required))
                }
            }
            binding.meta.text = meta.joinToString(" · ")
        }
    }

    private enum class ControlCategory(val titleRes: Int) {
        FINAL(R.string.recordbook_final_section),
        HOMEWORK(R.string.recordbook_homework_section),
        TESTS(R.string.recordbook_tests_section),
        OTHER(R.string.recordbook_other_section)
    }

    private object Diff : DiffUtil.ItemCallback<ControlListItem>() {
        override fun areItemsTheSame(
            oldItem: ControlListItem,
            newItem: ControlListItem
        ): Boolean = when {
            oldItem is ControlListItem.Section && newItem is ControlListItem.Section -> {
                oldItem.titleRes == newItem.titleRes
            }
            oldItem is ControlListItem.Control && newItem is ControlListItem.Control -> {
                oldItem.value.id == newItem.value.id && oldItem.value.name == newItem.value.name
            }
            else -> false
        }

        override fun areContentsTheSame(
            oldItem: ControlListItem,
            newItem: ControlListItem
        ): Boolean = oldItem == newItem
    }

    private companion object {
        const val VIEW_TYPE_SECTION = 0
        const val VIEW_TYPE_CONTROL = 1
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern(
            "d MMMM",
            Locale.forLanguageTag("ru")
        )

        fun category(control: RecordbookControl): ControlCategory {
            val name = control.name.lowercase()
            return when {
                name.contains("дополнитель") || name == "зачет" || name == "зачёт" ||
                    name.contains("экзамен") -> ControlCategory.FINAL
                name.contains("homework") || name.contains("домаш") -> ControlCategory.HOMEWORK
                name.contains("test") || name.contains("practice") ||
                    name.contains("контроль") -> ControlCategory.TESTS
                else -> ControlCategory.OTHER
            }
        }

        fun formatNumber(value: Double): String = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
        }.format(value)
    }
}
