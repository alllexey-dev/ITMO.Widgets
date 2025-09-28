package me.alllexey123.itmowidgets.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import api.myitmo.model.Lesson
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.utils.ScheduleUtils
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

class LessonAdapter(private val scheduleList: List<ScheduleItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_LESSON = 1
        private const val VIEW_TYPE_BREAK = 2
    }

    override fun getItemViewType(position: Int): Int {
        if (scheduleList.isEmpty()) return VIEW_TYPE_LESSON
        return when (scheduleList[position]) {
            is ScheduleItem.LessonItem -> VIEW_TYPE_LESSON
            is ScheduleItem.BreakItem -> VIEW_TYPE_BREAK
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_LESSON -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.single_lesson_widget_variant_list, parent, false)
                LessonViewHolder(view)
            }

            VIEW_TYPE_BREAK -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_schedule_break, parent, false)
                BreakViewHolder(view)
            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (scheduleList.isEmpty()) {
            bindEmptyDay(holder as LessonViewHolder)
            return
        }
        when (val item = scheduleList[position]) {
            is ScheduleItem.LessonItem -> {
                bindLesson(holder as LessonViewHolder, item.lesson, position)
            }

            is ScheduleItem.BreakItem -> {
                bindBreak(holder as BreakViewHolder, item.from, item.to)
            }
        }
    }

    private fun bindEmptyDay(holder: LessonViewHolder) {
        holder.lessonTitle.text = "В этот день нет пар"
        holder.typeIndicator.setColorFilter(
            ContextCompat.getColor(holder.itemView.context, R.color.free_color)
        )
        holder.teacherLayout.visibility = View.GONE
        holder.locationLayout.visibility = View.GONE
        holder.timeLayout.visibility = View.GONE
        holder.divider.visibility = View.GONE
        return
    }

    private fun bindLesson(holder: LessonViewHolder, lesson: Lesson, position: Int) {
        holder.lessonTitle.text = lesson.subject ?: "Неизвестная дисциплина"

        holder.typeIndicator.setColorFilter(
            ContextCompat.getColor(
                holder.itemView.context,
                ScheduleUtils.getWorkTypeColor(lesson.workTypeId)
            )
        )

        if (lesson.teacherName != null) {
            holder.teacherName.text = lesson.teacherName
            holder.teacherLayout.visibility = View.VISIBLE
        } else {
            holder.teacherLayout.visibility = View.GONE
        }

        if (lesson.room != null || lesson.building != null) {
            holder.locationRoom.text =
                if (lesson.room == null) "" else ScheduleUtils.shortenRoom(lesson.room!!) + ", "
            holder.locationBuilding.text =
                if (lesson.building == null) "" else ScheduleUtils.shortenBuildingName(lesson.building!!)
            holder.locationLayout.visibility = View.VISIBLE
        } else {
            holder.locationLayout.visibility = View.GONE
        }

        if (lesson.timeStart != null) {
            holder.time.text = "${lesson.timeStart} - ${lesson.timeEnd}"
            holder.timeLayout.visibility = View.VISIBLE
        } else {
            holder.timeLayout.visibility = View.GONE
        }

        if (position == scheduleList.size - 1 || (scheduleList.getOrNull(position + 1) != null && scheduleList.getOrNull(position + 1) is ScheduleItem.BreakItem)) {
            holder.divider.visibility = View.GONE
        } else {
            holder.divider.visibility = View.VISIBLE
        }
    }

    private fun bindBreak(holder: BreakViewHolder, from: LocalTime, to: LocalTime) {
        val dtf = DateTimeFormatter.ofPattern("HH:mm")
        holder.breakText.text = "⋯  можно отдохнуть с ${dtf.format(from)} до ${dtf.format(to)}  ⋯"
    }

    override fun getItemCount() = max(1, scheduleList.size)

    inner class LessonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val typeIndicator: ImageView = itemView.findViewById(R.id.type_indicator)

        val lessonTitle: TextView = itemView.findViewById(R.id.title)

        val teacherLayout: LinearLayout = itemView.findViewById(R.id.teacher_layout)
        val teacherName: TextView = itemView.findViewById(R.id.teacher)

        val locationLayout: LinearLayout = itemView.findViewById(R.id.location_layout)

        val locationRoom: TextView = itemView.findViewById(R.id.location_room)

        val locationBuilding: TextView = itemView.findViewById(R.id.location_building)

        val timeLayout: LinearLayout = itemView.findViewById(R.id.time_layout)

        val time: TextView = itemView.findViewById(R.id.time)

        val divider: ImageView = itemView.findViewById(R.id.divider)


    }

    inner class BreakViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val breakText: TextView = itemView.findViewById(R.id.break_text)

    }
}