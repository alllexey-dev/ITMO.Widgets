package me.alllexey123.itmowidgets.ui.schedule

import android.os.Handler
import android.os.Looper
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
import me.alllexey123.itmowidgets.util.ScheduleUtils
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class LessonAdapter(private val scheduleList: List<ScheduleItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_LESSON = 1
        private const val VIEW_TYPE_BREAK = 2
        private const val VIEW_TYPE_NO_LESSONS = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (scheduleList[position]) {
            is ScheduleItem.LessonItem -> VIEW_TYPE_LESSON
            is ScheduleItem.BreakItem -> VIEW_TYPE_BREAK
            is ScheduleItem.NoLessonsItem -> VIEW_TYPE_NO_LESSONS
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_LESSON -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_schedule_lesson, parent, false)
                LessonViewHolder(view)
            }

            VIEW_TYPE_BREAK -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_schedule_break, parent, false)
                BreakViewHolder(view)
            }

            VIEW_TYPE_NO_LESSONS -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_schedule_lesson, parent, false)
                LessonViewHolder(view)
            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = scheduleList[position]) {
            is ScheduleItem.LessonItem -> {
                bindLesson(holder as LessonViewHolder, item.lesson, item.lessonState, position)
            }

            is ScheduleItem.BreakItem -> {
                bindBreak(holder as BreakViewHolder, item.from, item.to)
            }

            is ScheduleItem.NoLessonsItem -> {
                bindEmptyDay(holder as LessonViewHolder, item.lessonState)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is LessonViewHolder) {
            holder.stopBlinking()
        }
    }

    private fun bindEmptyDay(holder: LessonViewHolder, lessonState: ScheduleItem.LessonState) {
        holder.stopBlinking()
        holder.lessonTitle.text = "В этот день нет пар"
        val typeIndicatorDrawable = when (lessonState) {
            ScheduleItem.LessonState.COMPLETED -> R.drawable.indicator_circle
            ScheduleItem.LessonState.CURRENT -> {
                holder.startBlinking()
                R.drawable.indicator_circle_hollow_dot
            }
            ScheduleItem.LessonState.UPCOMING -> R.drawable.indicator_circle_hollow
        }

        holder.typeIndicator.setImageResource(typeIndicatorDrawable)
        holder.typeIndicator.setColorFilter(
            ContextCompat.getColor(holder.itemView.context, R.color.free_color)
        )
        holder.teacherLayout.visibility = View.GONE
        holder.locationLayout.visibility = View.GONE
        holder.timeLayout.visibility = View.GONE
        holder.divider.visibility = View.GONE
        return
    }

    private fun bindLesson(
        holder: LessonViewHolder,
        lesson: Lesson,
        lessonState: ScheduleItem.LessonState,
        position: Int
    ) {
        holder.stopBlinking()
        holder.lessonTitle.text = lesson.subject ?: "Неизвестная дисциплина"

        val typeIndicatorDrawable = when (lessonState) {
            ScheduleItem.LessonState.COMPLETED -> R.drawable.indicator_circle
            ScheduleItem.LessonState.CURRENT -> {
                holder.startBlinking()
                R.drawable.indicator_circle_hollow_dot
            }
            ScheduleItem.LessonState.UPCOMING -> R.drawable.indicator_circle_hollow
        }

        holder.typeIndicator.setImageResource(typeIndicatorDrawable)

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
            holder.locationRoom.text = "нет кабинета"
            holder.locationBuilding.text = ""
            holder.locationLayout.visibility = View.VISIBLE
        }

        if (lesson.timeStart != null) {
            holder.time.text = "${lesson.timeStart} - ${lesson.timeEnd}"
            holder.timeLayout.visibility = View.VISIBLE
        } else {
            holder.timeLayout.visibility = View.GONE
        }

        if (position == scheduleList.size - 1 || (scheduleList.getOrNull(position + 1) != null && scheduleList.getOrNull(
                position + 1
            ) is ScheduleItem.BreakItem)
        ) {
            holder.divider.visibility = View.GONE
        } else {
            holder.divider.visibility = View.VISIBLE
        }
    }

    private fun bindBreak(holder: BreakViewHolder, from: LocalTime, to: LocalTime) {
        val dtf = DateTimeFormatter.ofPattern("HH:mm")
        holder.breakText.text = "⋯  можно отдохнуть с ${dtf.format(from)} до ${dtf.format(to)}  ⋯"
    }

    override fun getItemCount() = scheduleList.size

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

        private val handler = Handler(Looper.getMainLooper())
        private var isHollow = false
        private val blinkingRunnable = object : Runnable {
            override fun run() {
                if (isHollow) {
                    typeIndicator.setImageResource(R.drawable.indicator_circle_hollow_dot)
                } else {
                    typeIndicator.setImageResource(R.drawable.indicator_circle_hollow)
                }
                isHollow = !isHollow
                handler.postDelayed(this, 700)
            }
        }

        fun startBlinking() {
            isHollow = false
            handler.post(blinkingRunnable)
        }

        fun stopBlinking() {
            handler.removeCallbacks(blinkingRunnable)
        }
    }

    inner class BreakViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val breakText: TextView = itemView.findViewById(R.id.break_text)
    }
}