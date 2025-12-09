package dev.alllexey.itmowidgets.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import api.myitmo.model.schedule.Lesson
import api.myitmo.model.schedule.Schedule
import com.google.android.material.card.MaterialCardView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.util.ScheduleUtils
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class DayScheduleAdapter :
    ListAdapter<Schedule, DayScheduleAdapter.DayViewHolder>(ScheduleDiffCallback) {

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

        holder.dayTitle.text = ScheduleUtils.getRuDayOfWeek(date.dayOfWeek).replaceFirstChar { it.uppercase() }
        holder.dayDate.text = "${date.dayOfMonth} ${ScheduleUtils.getRussianMonthInGenitiveCase(date.monthValue)}"

        holder.numberOfLessons.text = if (lessons.isEmpty()) "Нет пар" else "${lessons.size} ${ScheduleUtils.lessonDeclension(lessons.size)}"

        val today = LocalDate.now()
        val isToday = date.equals(today)

        if (isToday) {
            holder.card.strokeWidth = 3
            holder.card.strokeColor = context.getColorFromAttr(android.R.attr.colorPrimary)
            holder.dayTitle.setTextColor(context.getColorFromAttr(android.R.attr.colorPrimary))
            holder.card.elevation = 8f
            holder.numberOfLessons.setBackgroundResource(R.drawable.shape_pill_outline_selected)
        } else {
            holder.card.strokeWidth = 0
            holder.dayTitle.setTextColor(context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
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
        notifyItemRangeChanged(0, itemCount, "PAYLOAD_UPDATE_TIME")
    }

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.day_card)
        val itemRoot: LinearLayout = itemView.findViewById(R.id.day_card_root)
        val dayTitle: TextView = itemView.findViewById(R.id.day_title)
        val numberOfLessons: TextView = itemView.findViewById(R.id.number_of_lessons)
        val dayDate: TextView = itemView.findViewById(R.id.day_date)
        val innerRecyclerView: RecyclerView = itemView.findViewById(R.id.inner_recycler_view)
    }

    private fun processLessonsWithBreaks(lessons: List<Lesson>, date: LocalDate): List<ScheduleItem> {
        val now = LocalDateTime.now()
        val processedList = mutableListOf<ScheduleItem>()
        val sortedLessons = lessons.sortedBy { it.timeStart }

        sortedLessons.forEachIndexed { index, currentLesson ->
            val lessonStartTime = ScheduleUtils.parseTime(date, currentLesson.timeStart)
            val lessonEndTime = ScheduleUtils.parseTime(date, currentLesson.timeEnd)

            val lessonState = when {
                lessonEndTime < now -> ScheduleItem.LessonState.COMPLETED
                lessonStartTime <= now && lessonEndTime >= now -> ScheduleItem.LessonState.CURRENT
                else -> ScheduleItem.LessonState.UPCOMING
            }

            processedList.add(ScheduleItem.LessonItem(currentLesson, lessonState))

            if (index < sortedLessons.size - 1) {
                val nextLesson = sortedLessons[index + 1]
                try {
                    val currentEndTime =
                        LocalTime.parse(currentLesson.timeEnd, DateTimeFormatter.ofPattern("HH:mm"))
                    val nextStartTime =
                        LocalTime.parse(nextLesson.timeStart, DateTimeFormatter.ofPattern("HH:mm"))
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
        private val ScheduleDiffCallback = object : DiffUtil.ItemCallback<Schedule>() {
            override fun areItemsTheSame(oldItem: Schedule, newItem: Schedule) = oldItem.date == newItem.date
            override fun areContentsTheSame(oldItem: Schedule, newItem: Schedule) = oldItem == newItem
        }
    }

    private fun android.content.Context.getColorFromAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}