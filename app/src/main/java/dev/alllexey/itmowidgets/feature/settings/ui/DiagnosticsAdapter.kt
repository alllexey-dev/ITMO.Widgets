package dev.alllexey.itmowidgets.feature.settings.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticEntry
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticLevel
import dev.alllexey.itmowidgets.databinding.ItemDiagnosticEntryBinding

class DiagnosticsAdapter(
    private val formatTime: (DiagnosticEntry) -> String
) : ListAdapter<DiagnosticEntry, DiagnosticsAdapter.EntryViewHolder>(Diff) {

    private val expanded = mutableSetOf<DiagnosticEntry>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder =
        EntryViewHolder(ItemDiagnosticEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) = holder.bind(getItem(position))

    inner class EntryViewHolder(private val binding: ItemDiagnosticEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: DiagnosticEntry) = with(binding) {
            val context = root.context
            time.text = formatTime(entry)
            level.text = context.getString(when (entry.level) {
                DiagnosticLevel.WARNING -> R.string.diagnostics_level_warning
                DiagnosticLevel.ERROR -> R.string.diagnostics_level_error
                DiagnosticLevel.CRASH -> R.string.diagnostics_level_crash
            })
            level.setTextColor(MaterialColors.getColor(level, if (entry.level == DiagnosticLevel.WARNING) {
                com.google.android.material.R.attr.colorOnSurfaceVariant
            } else {
                android.R.attr.colorError
            }))
            tag.text = entry.tag
            message.text = entry.message
            val hasTrace = !entry.stackTrace.isNullOrBlank()
            stackTrace.text = entry.stackTrace
            stackTrace.isVisible = hasTrace && entry in expanded
            root.isClickable = hasTrace
            root.setOnClickListener(if (hasTrace) {
                {
                    if (!expanded.remove(entry)) expanded.add(entry)
                    stackTrace.isVisible = entry in expanded
                }
            } else null)
        }
    }

    private object Diff : DiffUtil.ItemCallback<DiagnosticEntry>() {
        override fun areItemsTheSame(oldItem: DiagnosticEntry, newItem: DiagnosticEntry) = oldItem == newItem
        override fun areContentsTheSame(oldItem: DiagnosticEntry, newItem: DiagnosticEntry) = oldItem == newItem
    }
}
