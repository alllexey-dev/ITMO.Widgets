package dev.alllexey.itmowidgets.feature.sport.ui.sign

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.databinding.ItemCalendarWeekBinding
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.CalendarDay

class SportSignWeekPagerAdapter(
    private val onDateClick: (java.time.LocalDate) -> Unit
) : RecyclerView.Adapter<SportSignWeekPagerAdapter.WeekViewHolder>() {

    private var weeks: List<List<CalendarDay>> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun submitWeeks(newWeeks: List<List<CalendarDay>>) {
        weeks = newWeeks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val binding = ItemCalendarWeekBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WeekViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        holder.bind(weeks[position])
    }

    override fun getItemCount(): Int = weeks.size

    override fun getItemId(position: Int): Long {
        return weeks[position].firstOrNull()?.date?.toEpochDay() ?: RecyclerView.NO_ID
    }

    inner class WeekViewHolder(
        binding: ItemCalendarWeekBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val daysAdapter = SportSignCalendarAdapter(onDateClick)
        private val daysRecycler = binding.weekDaysRecyclerView

        init {
            daysRecycler.adapter = daysAdapter
            // A fixed span count sizes every cell to exactly one seventh of the row
            // during the first measure pass, so the strip never has to be re-measured
            // after the destination becomes visible.
            daysRecycler.layoutManager = GridLayoutManager(itemView.context, DAYS_IN_WEEK)
            daysRecycler.itemAnimator = null
        }

        fun bind(days: List<CalendarDay>) {
            daysAdapter.submitList(days)
        }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
    }
}
