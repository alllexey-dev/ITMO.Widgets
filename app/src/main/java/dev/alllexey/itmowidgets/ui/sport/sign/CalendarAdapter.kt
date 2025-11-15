package dev.alllexey.itmowidgets.ui.sport.sign

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.databinding.ItemCalendarDayBinding
import java.time.LocalDate

class CalendarAdapter(
    private val onDateClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    private var days: List<CalendarDay> = emptyList()

    fun submitList(newDays: List<CalendarDay>) {
        days = newDays
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val layoutParams = binding.root.layoutParams
        layoutParams.width = parent.width / 7
        binding.root.layoutParams = layoutParams
        return CalendarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount(): Int = days.size

    inner class CalendarViewHolder(private val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onDateClick(days[adapterPosition].date)
                }
            }
        }

        fun bind(day: CalendarDay) {
            val context = itemView.context
            binding.dayOfWeekText.text = day.dayOfWeek
            binding.dayOfMonthText.text = day.dayOfMonth

            val textColor: Int
            val cardColor: Int

            when {
                day.isSelected -> {
                    cardColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, 0)
                    textColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer, 0)
                }
                day.isToday -> {
                    cardColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, 0)
                    textColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer, 0)
                }
                day.hasLessons -> {
                    cardColor = ContextCompat.getColor(context, android.R.color.transparent)
                    textColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0)
                }
                else -> {
                    cardColor = ContextCompat.getColor(context, android.R.color.transparent)
                    textColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0)
                    binding.dayOfMonthText.alpha = 0.5f
                    binding.dayOfWeekText.alpha = 0.5f
                }
            }

            binding.dayCard.setCardBackgroundColor(cardColor)
            binding.dayOfMonthText.setTextColor(textColor)

            if (day.hasLessons || day.isSelected || day.isToday) {
                binding.dayOfMonthText.alpha = 1.0f
                binding.dayOfWeekText.alpha = 1.0f
            }
        }
    }
}