package me.alllexey123.itmowidgets.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import api.myitmo.model.Lesson
import api.myitmo.model.Schedule
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.util.ScheduleUtils
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class DayScheduleAdapter(private var schedules: List<Schedule>) :
    RecyclerView.Adapter<DayScheduleAdapter.DayViewHolder>() {

    private val viewPool = RecyclerView.RecycledViewPool()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_schedule, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val daySchedule = schedules[position]
        holder.dayTitle.text = ScheduleUtils.getRuDayOfWeek(daySchedule.date.dayOfWeek)
        val dtf = DateTimeFormatter.ofPattern("dd.MM")
        holder.dayDate.text = dtf.format(daySchedule.date)

        val layoutManager = LinearLayoutManager(
            holder.innerRecyclerView.context,
            LinearLayoutManager.VERTICAL,
            false
        )

        val processed = processLessonsWithBreaks(daySchedule.lessons)
        layoutManager.initialPrefetchItemCount = processed.size

        val lessonAdapter = LessonAdapter(processed)

        holder.innerRecyclerView.layoutManager = layoutManager
        holder.innerRecyclerView.adapter = lessonAdapter
        holder.innerRecyclerView.setRecycledViewPool(viewPool)
    }

    fun updateData(newDaySchedules: List<Schedule>) {
        val oldDaySchedules = this.schedules
        this.schedules = newDaySchedules
        if (oldDaySchedules != newDaySchedules)notifyDataSetChanged()
    }

    override fun getItemCount(): Int = schedules.size

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayTitle: TextView = itemView.findViewById(R.id.dayTitleTextView)

        val dayDate: TextView = itemView.findViewById(R.id.dayDateTextView)
        val innerRecyclerView: RecyclerView = itemView.findViewById(R.id.innerRecyclerView)
    }

    fun processLessonsWithBreaks(lessons: List<Lesson>): List<ScheduleItem> {
        val BIG_BREAK_THRESHOLD = Duration.ofMinutes(60)

        val processedList = mutableListOf<ScheduleItem>()
        val sortedLessons = lessons.sortedBy { it.timeStart }

        sortedLessons.forEachIndexed { index, currentLesson ->
            processedList.add(ScheduleItem.LessonItem(currentLesson))

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

        return processedList
    }

    fun getItemAt(position: Int): Schedule? {
        return schedules.getOrNull(position)
    }
}