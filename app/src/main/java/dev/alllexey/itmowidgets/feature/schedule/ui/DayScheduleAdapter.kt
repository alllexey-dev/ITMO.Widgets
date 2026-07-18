package dev.alllexey.itmowidgets.feature.schedule.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.ScheduleUtil
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule
import dev.alllexey.itmowidgets.domain.model.schedule.Lesson
import java.time.Duration
import java.time.LocalDate

class DayScheduleAdapter(
    private val timeProvider: AcademicTimeProvider
) :
    ListAdapter<DaySchedule, DayScheduleAdapter.DayViewHolder>(ScheduleDiffCallback) {

    private val viewPool = RecyclerView.RecycledViewPool()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_schedule, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val daySchedule = getItem(position)
        val date = daySchedule.date
        val lessons = daySchedule.lessons
        val context = holder.itemView.context

        holder.dayTitle.text = ScheduleUtil.getRuDayOfWeek(date.dayOfWeek).replaceFirstChar { it.uppercase() }
        holder.dayDate.text = "${date.dayOfMonth} ${ScheduleUtil.getRussianMonthInGenitiveCase(date.monthValue)}"

        holder.numberOfLessons.text = if (lessons.isEmpty()) "Нет пар" else "${lessons.size} ${ScheduleUtil.lessonDeclension(lessons.size)}"

        val today = timeProvider.today()
        val isToday = date.equals(today)

        val hightlightColor = context.color.primary
        if (isToday) {
            holder.card.strokeWidth = 3
            holder.card.strokeColor = hightlightColor
            holder.dayTitle.setTextColor(hightlightColor)
            holder.card.elevation = 8f
            holder.numberOfLessons.setBackgroundResource(R.drawable.shape_pill_outline_selected)
        } else {
            holder.card.strokeWidth = 0
            holder.dayTitle.setTextColor(context.color.onSurface)
            holder.card.elevation = 0f
            holder.numberOfLessons.setBackgroundResource(R.drawable.shape_pill_outline)
        }

        if (date.isBefore(today)) {
            holder.itemRoot.alpha = 0.5f
        } else {
            holder.itemRoot.alpha = 1.0f
        }

        val layoutManager = LinearLayoutManager(
            holder.innerRecyclerView.context,
            LinearLayoutManager.VERTICAL,
            false
        )

        val processed = processLessonsWithBreaks(lessons, date)
        layoutManager.initialPrefetchItemCount = processed.size
        val lessonAdapter = LessonAdapter(processed)

        holder.innerRecyclerView.layoutManager = layoutManager
        holder.innerRecyclerView.adapter = lessonAdapter
        holder.innerRecyclerView.setRecycledViewPool(viewPool)
    }

    fun updateLessonStates() {
        // it can be optimized
        notifyItemRangeChanged(0, itemCount)
    }

    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.day_card)
        val itemRoot: LinearLayout = itemView.findViewById(R.id.day_card_root)
        val dayTitle: TextView = itemView.findViewById(R.id.day_title)
        val numberOfLessons: TextView = itemView.findViewById(R.id.number_of_lessons)
        val dayDate: TextView = itemView.findViewById(R.id.day_date)
        val innerRecyclerView: RecyclerView = itemView.findViewById(R.id.inner_recycler_view)
    }

    private fun processLessonsWithBreaks(lessons: List<Lesson>, date: LocalDate): List<ScheduleItem> {
        val now = timeProvider.now().toLocalDateTime()
        val processedList = mutableListOf<ScheduleItem>()
        val sortedLessons = lessons.sortedBy { it.start }

        sortedLessons.forEachIndexed { index, currentLesson ->
            val lessonStartTime = currentLesson.start.atDate(date)
            val lessonEndTime = currentLesson.end.atDate(date)

            val lessonState = when {
                lessonEndTime < now -> ScheduleItem.LessonState.COMPLETED
                now in lessonStartTime..lessonEndTime -> ScheduleItem.LessonState.CURRENT
                else -> ScheduleItem.LessonState.UPCOMING
            }

            processedList.add(ScheduleItem.LessonItem(currentLesson, lessonState, index == sortedLessons.size - 1))

            if (index < sortedLessons.size - 1) {
                val nextLesson = sortedLessons[index + 1]
                try {
                    val currentEndTime = currentLesson.end
                    val nextStartTime = nextLesson.start
                    val breakDuration = Duration.between(currentEndTime, nextStartTime)
                    if (breakDuration > BIG_BREAK_THRESHOLD) {
                        processedList.add(ScheduleItem.BreakItem(currentEndTime, nextStartTime))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (processedList.isEmpty()) {
            return listOf(ScheduleItem.NoLessonsItem(ScheduleItem.LessonState.COMPLETED))
        }

        return processedList
    }

    companion object {
        private val BIG_BREAK_THRESHOLD = Duration.ofMinutes(60)
        private val ScheduleDiffCallback = object : DiffUtil.ItemCallback<DaySchedule>() {
            override fun areItemsTheSame(oldItem: DaySchedule, newItem: DaySchedule) = oldItem.date == newItem.date
            override fun areContentsTheSame(oldItem: DaySchedule, newItem: DaySchedule) = oldItem == newItem
        }
    }
}
