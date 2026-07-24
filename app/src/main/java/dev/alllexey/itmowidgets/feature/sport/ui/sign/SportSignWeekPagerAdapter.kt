package dev.alllexey.itmowidgets.feature.sport.ui.sign

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.databinding.ItemCalendarWeekBinding
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.CalendarDay

class SportSignWeekPagerAdapter(
    private val onDateClick: (java.time.LocalDate) -> Unit
) : RecyclerView.Adapter<SportSignWeekPagerAdapter.WeekViewHolder>() {

    private var weeks: List<List<CalendarDay>> = emptyList()
    private var pageWidthPx: Int = 0

    init {
        setHasStableIds(true)
    }

    fun submitWeeks(newWeeks: List<List<CalendarDay>>) {
        weeks = newWeeks
        notifyDataSetChanged()
    }

    fun setPageWidth(widthPx: Int) {
        if (widthPx <= 0 || pageWidthPx == widthPx) return
        pageWidthPx = widthPx
        notifyItemRangeChanged(0, itemCount, WIDTH_PAYLOAD)
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

    override fun onBindViewHolder(
        holder: WeekViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(WIDTH_PAYLOAD)) {
            holder.updateDayWidth(pageWidthPx)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
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
            daysRecycler.layoutManager = LinearLayoutManager(
                itemView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            daysRecycler.itemAnimator = null
            daysRecycler.addOnLayoutChangeListener {
                    recycler, left, _, right, _, oldLeft, _, oldRight, _ ->
                val width = right - left
                val oldWidth = oldRight - oldLeft
                if (width > 0 && width != oldWidth) {
                    recycler.post { updateDayWidth(width) }
                }
            }
        }

        fun bind(days: List<CalendarDay>) {
            daysAdapter.submitList(days)
            updateDayWidth(pageWidthPx.takeIf { it > 0 } ?: daysRecycler.width)
        }

        fun updateDayWidth(widthPx: Int) {
            if (widthPx > 0) {
                daysAdapter.setItemWidth(widthPx / DAYS_IN_WEEK)
            }
        }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
        val WIDTH_PAYLOAD = Any()
    }
}
