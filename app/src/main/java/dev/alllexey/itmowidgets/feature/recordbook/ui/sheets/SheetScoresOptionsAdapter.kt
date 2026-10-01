package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.core.ui.bindSelectionAccessibility
import dev.alllexey.itmowidgets.databinding.ItemSheetScoresOptionBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectLinkSectionBinding

/** A row of the «Мои баллы» sheet: a tab heading or one choice. */
sealed interface SheetOption {
    val id: String

    data class Section(override val id: String, val title: String) : SheetOption

    /** [value] sits at the end of the row; [selected] is the current choice. */
    data class Choice(
        override val id: String,
        val title: String,
        val caption: String? = null,
        val value: String? = null,
        val selected: Boolean = false,
        val onClick: () -> Unit,
    ) : SheetOption {
        override fun equals(other: Any?): Boolean = other is Choice && other.id == id && other.title == title &&
            other.caption == caption && other.value == value && other.selected == selected

        override fun hashCode(): Int = id.hashCode()
    }
}

/** Tab headings and choices; the whole choice row is the 48 dp target and every property is set on bind. */
class SheetScoresOptionsAdapter : ListAdapter<SheetOption, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SheetOption.Section -> TYPE_SECTION
        is SheetOption.Choice -> TYPE_CHOICE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SECTION) {
            SectionHolder(ItemSubjectLinkSectionBinding.inflate(inflater, parent, false).root)
        } else {
            ChoiceHolder(ItemSheetScoresOptionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SheetOption.Section -> (holder as SectionHolder).bind(item)
            is SheetOption.Choice -> (holder as ChoiceHolder).bind(item)
        }
    }

    private class SectionHolder(private val title: TextView) : RecyclerView.ViewHolder(title) {
        fun bind(item: SheetOption.Section) {
            title.text = item.title
            title.setCompoundDrawablesRelative(null, null, null, null)
        }
    }

    private class ChoiceHolder(private val binding: ItemSheetScoresOptionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SheetOption.Choice) = with(binding) {
            title.text = item.title
            caption.text = item.caption
            caption.isVisible = item.caption != null
            value.text = item.value
            value.isVisible = !item.value.isNullOrEmpty()
            check.visibility = if (item.selected) View.VISIBLE else View.INVISIBLE
            val label = listOfNotNull(item.title, item.caption, item.value?.takeIf { it.isNotEmpty() }).joinToString(", ")
            root.bindSelectionAccessibility(label, item.selected)
            root.setOnClickListener { item.onClick() }
        }
    }

    private object Diff : DiffUtil.ItemCallback<SheetOption>() {
        override fun areItemsTheSame(oldItem: SheetOption, newItem: SheetOption) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SheetOption, newItem: SheetOption) = oldItem == newItem
    }

    private companion object {
        const val TYPE_SECTION = 0
        const val TYPE_CHOICE = 1
    }
}
