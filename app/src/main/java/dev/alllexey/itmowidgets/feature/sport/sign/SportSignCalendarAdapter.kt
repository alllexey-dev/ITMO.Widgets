package dev.alllexey.itmowidgets.feature.sport.sign

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R as AppR
import dev.alllexey.itmowidgets.databinding.ItemCalendarDayBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SportSignCalendarAdapter(
    private val onDateClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<SportSignCalendarAdapter.CalendarViewHolder>() {

    private var days: List<CalendarDay> = emptyList()
    private var itemWidthPx: Int = 0

    fun submitList(newDays: List<CalendarDay>) {
        days = newDays
        notifyDataSetChanged()
    }

    fun setItemWidth(widthPx: Int) {
        if (itemWidthPx != widthPx && widthPx > 0) {
            itemWidthPx = widthPx
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CalendarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        holder.bind(days[position], itemWidthPx)
    }

    override fun getItemCount(): Int = days.size

    inner class CalendarViewHolder(
        private val binding: ItemCalendarDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                val pos = absoluteAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDateClick(days[pos].date)
                }
            }
        }

        fun bind(day: CalendarDay, itemWidthPx: Int) {
            val context = itemView.context

            if (itemWidthPx > 0) {
                binding.root.layoutParams = binding.root.layoutParams.apply {
                    width = itemWidthPx
                }
            }

            binding.dayOfMonthText.alpha = 1f
            binding.dayOfWeekText.alpha = 1f

            binding.dayOfWeekText.text = day.dayOfWeek
            binding.dayOfMonthText.text = day.dayOfMonth
            itemView.isSelected = day.isSelected
            val formattedDate = day.date.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            )
            val dateDescription = if (day.isToday) {
                context.getString(AppR.string.sport_calendar_today, formattedDate)
            } else {
                formattedDate
            }
            itemView.contentDescription = context.getString(
                when {
                    day.hasAvailableLessons -> AppR.string.sport_calendar_available
                    day.hasLessons -> AppR.string.sport_calendar_has_lessons
                    else -> AppR.string.sport_calendar_empty
                },
                dateDescription
            )

            val textColor: Int
            val cardColor: Int

            when {
                day.isSelected -> {
                    cardColor = MaterialColors.getColor(context, R.attr.colorPrimaryContainer, 0)
                    textColor = MaterialColors.getColor(context, R.attr.colorOnPrimaryContainer, 0)
                }
                day.isToday -> {
                    cardColor = MaterialColors.getColor(context, R.attr.colorTertiaryContainer, 0)
                    textColor = MaterialColors.getColor(context, R.attr.colorOnTertiaryContainer, 0)
                }
                day.hasAvailableLessons -> {
                    cardColor = MaterialColors.getColor(context, R.attr.colorSecondaryContainer, 0)
                    textColor = MaterialColors.getColor(context, R.attr.colorOnSecondaryContainer, 0)
                }
                day.hasLessons -> {
                    cardColor = ContextCompat.getColor(context, android.R.color.transparent)
                    textColor = MaterialColors.getColor(context, R.attr.colorOnSurface, 0)
                }
                else -> {
                    cardColor = ContextCompat.getColor(context, android.R.color.transparent)
                    textColor = MaterialColors.getColor(context, R.attr.colorOnSurface, 0)
                    binding.dayOfMonthText.alpha = 0.5f
                    binding.dayOfWeekText.alpha = 0.5f
                }
            }

            binding.dayCard.setCardBackgroundColor(cardColor)
            binding.dayOfMonthText.setTextColor(textColor)
            binding.dayOfWeekText.setTextColor(textColor)
        }
    }
}
