package dev.alllexey.itmowidgets.feature.sport.sign

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.PathInterpolator
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
        val oldDays = days
        days = newDays
        val representsSameWeek = oldDays.size == newDays.size &&
            oldDays.indices.all { oldDays[it].date == newDays[it].date }
        if (!representsSameWeek) {
            notifyDataSetChanged()
            return
        }

        newDays.indices.forEach { index ->
            val selectionChanged = oldDays[index].isSelected != newDays[index].isSelected
            notifyItemChanged(
                index,
                if (selectionChanged) SELECTION_PAYLOAD else CONTENT_PAYLOAD
            )
        }
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
        holder.bind(days[position], itemWidthPx, animateSelection = false)
    }

    override fun onBindViewHolder(
        holder: CalendarViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        holder.bind(
            days[position],
            itemWidthPx,
            animateSelection = payloads.contains(SELECTION_PAYLOAD)
        )
    }

    override fun getItemCount(): Int = days.size

    inner class CalendarViewHolder(
        private val binding: ItemCalendarDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var colorAnimator: ValueAnimator? = null

        init {
            itemView.setOnClickListener {
                val pos = absoluteAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDateClick(days[pos].date)
                }
            }
        }

        fun bind(day: CalendarDay, itemWidthPx: Int, animateSelection: Boolean) {
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

            colorAnimator?.cancel()
            binding.dayCard.animate().cancel()
            if (animateSelection) {
                animateSelection(day, cardColor, textColor)
            } else {
                binding.dayCard.scaleX = 1f
                binding.dayCard.scaleY = 1f
                applyColors(cardColor, textColor)
            }
        }

        private fun animateSelection(day: CalendarDay, cardColor: Int, textColor: Int) {
            val startCardColor = binding.dayCard.cardBackgroundColor.defaultColor
            val startTextColor = binding.dayOfMonthText.currentTextColor
            val evaluator = ArgbEvaluator()
            colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SELECTION_ANIMATION_DURATION
                interpolator = MOTION_INTERPOLATOR
                addUpdateListener { animator ->
                    val fraction = animator.animatedFraction
                    applyColors(
                        evaluator.evaluate(fraction, startCardColor, cardColor) as Int,
                        evaluator.evaluate(fraction, startTextColor, textColor) as Int
                    )
                }
                start()
            }

            if (day.isSelected) {
                binding.dayCard.scaleX = SELECTED_START_SCALE
                binding.dayCard.scaleY = SELECTED_START_SCALE
            }
            binding.dayCard.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(SELECTION_ANIMATION_DURATION)
                .setInterpolator(MOTION_INTERPOLATOR)
                .start()
        }

        private fun applyColors(cardColor: Int, textColor: Int) {
            binding.dayCard.setCardBackgroundColor(cardColor)
            binding.dayOfMonthText.setTextColor(textColor)
            binding.dayOfWeekText.setTextColor(textColor)
        }
    }

    private companion object {
        val CONTENT_PAYLOAD = Any()
        val SELECTION_PAYLOAD = Any()
        val MOTION_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
        const val SELECTED_START_SCALE = 0.82f
        const val SELECTION_ANIMATION_DURATION = 220L
    }
}
