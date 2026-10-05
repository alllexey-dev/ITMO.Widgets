package dev.alllexey.itmowidgets.feature.schedule.ui.changes

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.alignRailIcon
import dev.alllexey.itmowidgets.core.ui.lessonTypeNameRes
import dev.alllexey.itmowidgets.core.ui.listLines
import dev.alllexey.itmowidgets.core.ui.listSummary
import dev.alllexey.itmowidgets.databinding.ItemScheduleChangeBinding
import dev.alllexey.itmowidgets.databinding.ItemScheduleChangeDayBinding
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.RelativeDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangeDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangeRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.toJavaLocalDate

/** One history entry: a day title or a change found that day. */
internal sealed interface ScheduleChangeItem {
    val key: String

    data class Day(val date: LocalDate, val relative: RelativeDay) : ScheduleChangeItem {
        override val key: String get() = "day-$date"
    }

    data class Change(val row: ScheduleChangeRow) : ScheduleChangeItem {
        override val key: String get() = "change-${row.change.id}"
    }
}

internal fun List<ScheduleChangeDay>.toItems(): List<ScheduleChangeItem> = flatMap { day ->
    listOf(ScheduleChangeItem.Day(day.date.toJavaLocalDate(), day.relative)) + day.rows.map(ScheduleChangeItem::Change)
}

/** The history rows are information only: nothing in them reacts to a tap. */
internal class ScheduleChangesAdapter : ListAdapter<ScheduleChangeItem, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ScheduleChangeItem.Day -> TYPE_DAY
        is ScheduleChangeItem.Change -> TYPE_CHANGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DAY) DayHolder(ItemScheduleChangeDayBinding.inflate(inflater, parent, false))
        else ChangeHolder(ItemScheduleChangeBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ScheduleChangeItem.Day -> (holder as DayHolder).bind(item)
            is ScheduleChangeItem.Change -> (holder as ChangeHolder).bind(item.row)
        }
    }

    private class DayHolder(private val binding: ItemScheduleChangeDayBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(day: ScheduleChangeItem.Day) {
            binding.dayTitle.text = dayTitle(binding.root.context, day)
        }
    }

    private class ChangeHolder(private val binding: ItemScheduleChangeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ScheduleChangeRow) = with(binding) {
            val context = root.context
            val change = row.change
            val subjectText = change.subjectName.trim().ifEmpty { context.getString(R.string.schedule_unknown_subject) }
            val summaryText = change.listSummary(context)
            val lineTexts = change.listLines(context)
            val metaText = listOfNotNull(
                context.getString(lessonTypeNameRes(change.typeId)),
                change.flowName?.trim()?.takeIf(String::isNotEmpty)
            ).joinToString(" · ")

            subject.text = subjectText
            newMark.isVisible = row.isNew
            alignRailIcon(newMark, subject)
            summary.text = summaryText
            bindLines(lineTexts)
            meta.text = metaText
            root.contentDescription = listOfNotNull(
                context.getString(R.string.schedule_change_new).takeIf { row.isNew },
                subjectText,
                summaryText,
                *lineTexts.toTypedArray(),
                metaText
            ).joinToString(". ")
            ViewCompat.setScreenReaderFocusable(root, true)
        }

        /** Reuses the line views a recycled row already has; each field starts on its own line. */
        private fun bindLines(texts: List<String>) = with(binding.lines) {
            while (childCount > texts.size) removeViewAt(childCount - 1)
            while (childCount < texts.size) {
                val line = LayoutInflater.from(context).inflate(R.layout.item_schedule_change_line, this, false)
                line.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = resources.getDimensionPixelSize(R.dimen.design_spacing_related)
                }
                addView(line)
            }
            texts.forEachIndexed { index, text -> (getChildAt(index) as TextView).text = text }
            isVisible = texts.isNotEmpty()
        }
    }

    private object Diff : DiffUtil.ItemCallback<ScheduleChangeItem>() {
        override fun areItemsTheSame(oldItem: ScheduleChangeItem, newItem: ScheduleChangeItem) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: ScheduleChangeItem, newItem: ScheduleChangeItem) = oldItem == newItem
    }

    private companion object {
        const val TYPE_DAY = 0
        const val TYPE_CHANGE = 1
    }
}

/** "Сегодня", "Вчера", "30 сентября"; another year is spelled out. */
private fun dayTitle(context: Context, day: ScheduleChangeItem.Day): String = when (day.relative) {
    RelativeDay.TODAY -> context.getString(R.string.home_schedule_today)
    RelativeDay.YESTERDAY -> context.getString(R.string.schedule_changes_yesterday)
    RelativeDay.OTHER -> day.date.format(DAY)
    RelativeDay.OTHER_YEAR -> day.date.format(DAY_WITH_YEAR)
}

private val RUSSIAN: Locale = Locale.forLanguageTag("ru")
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN)
private val DAY_WITH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", RUSSIAN)
