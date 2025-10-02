package me.alllexey123.itmowidgets.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import api.myitmo.model.Lesson
import api.myitmo.model.Schedule
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.util.ScheduleUtils
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

        holder.dayTitle.text = ScheduleUtils.getRuDayOfWeek(date.dayOfWeek)
        holder.dayDate.text = "${date.dayOfMonth} ${ScheduleUtils.getRussianMonthInGenitiveCase(date.monthValue)}"
        val numberOfLessonsText = if (lessons.isEmpty()) {
            "нет пар"
        } else {
            "${lessons.size} ${ScheduleUtils.lessonDeclension(lessons.size)}"
        }
        holder.numberOfLessons.text = numberOfLessonsText

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
        submitList(currentList.toList())
    }

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayTitle: TextView = itemView.findViewById(R.id.day_title)
        val numberOfLessons: TextView = itemView.findViewById(R.id.number_of_lessons)
        val dayDate: TextView = itemView.findViewById(R.id.day_date)
        val innerRecyclerView: RecyclerView = itemView.findViewById(R.id.inner_recycler_view)
    }

    private fun processLessonsWithBreaks(lessons: List<Lesson>, date: LocalDate): List<ScheduleItem> {
        val BIG_BREAK_THRESHOLD = Duration.ofMinutes(60)
        val now = LocalDateTime.now()
        val processedList = mutableListOf<ScheduleItem>()
        val sortedLessons = lessons.sortedBy { it.timeStart }

        sortedLessons.forEachIndexed { index, currentLesson ->
            val lessonStartTime = ScheduleUtils.parseTime(date, currentLesson.timeStart)
            val lessonEndTime = ScheduleUtils.parseTime(date, currentLesson.timeEnd)
            val lessonState = when {
                lessonEndTime < now -> ScheduleItem.LessonState.COMPLETED
                lessonStartTime < now -> ScheduleItem.LessonState.CURRENT
                else -> ScheduleItem.LessonState.UPCOMING
            }
            processedList.add(ScheduleItem.LessonItem(currentLesson, lessonState))

            if (index < sortedLessons.size - 1) {
                val nextLesson = sortedLessons[index + 1]
                try {
                    val currentEndTime = LocalTime.parse(currentLesson.timeEnd, DateTimeFormatter.ofPattern("HH:mm"))
                    val nextStartTime = LocalTime.parse(nextLesson.timeStart, DateTimeFormatter.ofPattern("HH:mm"))
                    val breakDuration = Duration.between(currentEndTime, nextStartTime)
                    if (breakDuration.abs() > BIG_BREAK_THRESHOLD) {
                        processedList.add(ScheduleItem.BreakItem(currentEndTime, nextStartTime))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (processedList.isEmpty()) {
            val nowDate = now.toLocalDate()
            val lessonState = when {
                date < nowDate -> ScheduleItem.LessonState.COMPLETED
                date > nowDate -> ScheduleItem.LessonState.UPCOMING
                else -> ScheduleItem.LessonState.CURRENT
            }
            return listOf(ScheduleItem.NoLessonsItem(lessonState))
        }

        return processedList
    }

    companion object {
        private val ScheduleDiffCallback = object : DiffUtil.ItemCallback<Schedule>() {
            override fun areItemsTheSame(oldItem: Schedule, newItem: Schedule): Boolean {
                return oldItem.date == newItem.date
            }

            override fun areContentsTheSame(oldItem: Schedule, newItem: Schedule): Boolean {
                return oldItem == newItem
            }
        }
    }
}