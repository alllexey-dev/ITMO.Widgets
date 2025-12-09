package dev.alllexey.itmowidgets.ui.schedule

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.util.ScheduleUtils
import dev.alllexey.itmowidgets.util.getColorFromAttr

class LessonAdapter(private val scheduleList: List<ScheduleItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_LESSON = 1
        private const val VIEW_TYPE_BREAK = 2
        private const val VIEW_TYPE_NO_LESSONS = 3
        private const val VIEW_TYPE_LESSON_LAST = 4
    }

    override fun getItemViewType(position: Int): Int {
        return when (scheduleList[position]) {
            is ScheduleItem.LessonItem -> if (scheduleList.size - 1 == position) VIEW_TYPE_LESSON_LAST else VIEW_TYPE_LESSON
            is ScheduleItem.BreakItem -> VIEW_TYPE_BREAK
            is ScheduleItem.NoLessonsItem -> VIEW_TYPE_NO_LESSONS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_LESSON -> LessonViewHolder(inflater.inflate(R.layout.item_schedule_lesson, parent, false))
            VIEW_TYPE_LESSON_LAST -> LessonViewHolder(inflater.inflate(R.layout.item_schedule_lesson_last, parent, false))
            VIEW_TYPE_BREAK -> BreakViewHolder(inflater.inflate(R.layout.item_schedule_break, parent, false))
            VIEW_TYPE_NO_LESSONS -> EmptyDayViewHolder(inflater.inflate(R.layout.item_schedule_empty, parent, false))
            else -> throw IllegalArgumentException("Invalid type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = scheduleList[position]) {
            is ScheduleItem.LessonItem -> (holder as LessonViewHolder).bind(item)
            is ScheduleItem.BreakItem -> (holder as BreakViewHolder).bind(item)
            is ScheduleItem.NoLessonsItem -> (holder as EmptyDayViewHolder).bind(item)
        }
    }

    override fun getItemCount() = scheduleList.size

    inner class LessonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeStart: TextView = itemView.findViewById(R.id.time_start)
        private val timeEnd: TextView = itemView.findViewById(R.id.time_end)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val teacherLayout: View = itemView.findViewById(R.id.teacher_layout)
        private val teacherName: TextView = itemView.findViewById(R.id.teacher)
        private val locationLayout: View = itemView.findViewById(R.id.location_layout)
        private val locationRoom: TextView = itemView.findViewById(R.id.location_room)
        private val locationBuilding: TextView = itemView.findViewById(R.id.location_building)
        private val noteLayout: View = itemView.findViewById(R.id.note_layout)
        private val noteText: TextView = itemView.findViewById(R.id.note)
        private val card: MaterialCardView = itemView.findViewById(R.id.card_container)
        private val timelineDot: ImageView = itemView.findViewById(R.id.timeline_dot)
        private val typeBadge: MaterialCardView = itemView.findViewById(R.id.type_badge)
        private val typeLabel: TextView = itemView.findViewById(R.id.type_label)

        fun bind(item: ScheduleItem.LessonItem) {
            val lesson = item.lesson

            title.text = lesson.subject ?: "Неизвестный предмет"
            timeStart.text = lesson.timeStart
            timeEnd.text = lesson.timeEnd

            if (lesson.teacherName != null) {
                teacherName.text = lesson.teacherName
                teacherLayout.visibility = View.VISIBLE
            } else {
                teacherLayout.visibility = View.GONE
            }

            if (lesson.note != null) {
                noteText.text = lesson.note?.trim()
                noteLayout.visibility = View.VISIBLE
            } else {
                noteLayout.visibility = View.GONE
            }

            if (lesson.room != null || lesson.building != null) {
                locationRoom.text = if (lesson.room != null) "${ScheduleUtils.shortenRoom(lesson.room!!)}" else ""
                locationBuilding.text = if (lesson.building != null) ScheduleUtils.shortenBuildingName(lesson.building!!) else ""
                locationLayout.visibility = View.VISIBLE
            } else {
                locationLayout.visibility = View.GONE
            }

            val typeColor = ContextCompat.getColor(itemView.context, ScheduleUtils.getWorkTypeColor(lesson.workTypeId))
            typeBadge.backgroundTintList = ColorStateList.valueOf(typeColor)
            typeLabel.text = ScheduleUtils.getWorkTypeName(lesson.workTypeId)
            typeLabel.setTextColor(Color.WHITE)

            val context = itemView.context

            val colorPrimaryContainer = context.getColorFromAttr(com.google.android.material.R.attr.colorPrimaryContainer)
            val colorOnPrimaryContainer = context.getColorFromAttr(com.google.android.material.R.attr.colorOnPrimaryContainer)
            val colorSurface = context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh)
            val colorOnSurface = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            val colorPrimary = context.getColorFromAttr(android.R.attr.colorPrimary)
            val colorOutline = context.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)

            when (item.lessonState) {
                ScheduleItem.LessonState.CURRENT -> {
                    card.setCardBackgroundColor(colorPrimaryContainer)
                    title.setTextColor(colorOnPrimaryContainer)

                    timelineDot.setColorFilter(colorPrimary)
                    timelineDot.scaleX = 1.3f
                    timelineDot.scaleY = 1.3f

                    timeStart.setTextColor(colorPrimary)
                }
                ScheduleItem.LessonState.COMPLETED -> {
                    card.setCardBackgroundColor(context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLowest))
                    title.setTextColor(context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    card.alpha = 0.7f

                    timelineDot.setColorFilter(colorOutline)
                    timelineDot.scaleX = 0.8f
                    timelineDot.scaleY = 0.8f

                    timeStart.setTextColor(context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
                }
                ScheduleItem.LessonState.UPCOMING -> {
                    card.setCardBackgroundColor(colorSurface)
                    title.setTextColor(colorOnSurface)
                    card.alpha = 1.0f

                    timelineDot.setColorFilter(colorOutline)
                    timelineDot.scaleX = 1.0f
                    timelineDot.scaleY = 1.0f

                    timeStart.setTextColor(colorOnSurface)
                }
            }
        }
    }

    inner class BreakViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val breakText: TextView = itemView.findViewById(R.id.break_text)

        fun bind(item: ScheduleItem.BreakItem) {
            breakText.text = "Перерыв с ${item.from} по ${item.to}"
        }
    }

    inner class EmptyDayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(item: ScheduleItem.NoLessonsItem) {
        }
    }
}