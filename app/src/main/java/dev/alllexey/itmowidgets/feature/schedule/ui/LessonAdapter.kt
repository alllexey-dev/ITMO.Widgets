package dev.alllexey.itmowidgets.feature.schedule.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.core.util.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

class LessonAdapter(private val scheduleList: List<ScheduleItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_LESSON = 1
        private const val VIEW_TYPE_BREAK = 2
        private const val VIEW_TYPE_NO_LESSONS = 3
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    }

    private var timelineGuideOffset: Int? = null

    override fun getItemViewType(position: Int): Int {
        return when (scheduleList[position]) {
            is ScheduleItem.LessonItem -> VIEW_TYPE_LESSON
            is ScheduleItem.BreakItem -> VIEW_TYPE_BREAK
            is ScheduleItem.NoLessonsItem -> VIEW_TYPE_NO_LESSONS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_LESSON -> LessonViewHolder(inflater.inflate(R.layout.item_schedule_lesson, parent, false))
            VIEW_TYPE_BREAK -> BreakViewHolder(inflater.inflate(R.layout.item_schedule_break, parent, false))
            VIEW_TYPE_NO_LESSONS -> EmptyDayViewHolder(inflater.inflate(R.layout.item_schedule_empty, parent, false))
            else -> throw IllegalArgumentException("Invalid type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = scheduleList[position]) {
            is ScheduleItem.LessonItem -> {
                updateTimelineGuide(holder.itemView, R.id.timeline_guide)
                (holder as LessonViewHolder).bind(item)
            }
            is ScheduleItem.BreakItem -> {
                updateTimelineGuide(holder.itemView, R.id.timeline_guide_break)
                (holder as BreakViewHolder).bind(item)
            }
            is ScheduleItem.NoLessonsItem -> (holder as EmptyDayViewHolder).bind(item)
        }
    }

    override fun getItemCount() = scheduleList.size

    private fun updateTimelineGuide(row: View, guideId: Int) {
        val offset = timelineGuideOffset ?: run {
            val timeLabel = row.findViewById<TextView>(R.id.time_start)
                ?: LayoutInflater.from(row.context).inflate(R.layout.item_schedule_lesson, FrameLayout(row.context), false)
                    .findViewById<TextView>(R.id.time_start)
            val widestTime = scheduleList.filterIsInstance<ScheduleItem.LessonItem>()
                .flatMap { listOf(it.lesson.start, it.lesson.end) }
                .maxOfOrNull { ceil(timeLabel.paint.measureText(it.format(TIME_FORMATTER))).toInt() } ?: 0
            val margin = (timeLabel.layoutParams as ViewGroup.MarginLayoutParams).marginEnd
            // Measure the actual themed/scaled font once, and keep breaks on the
            // same timeline. A fixed gutter clips time labels with larger fonts.
            maxOf(50.dp, widestTime + timeLabel.compoundPaddingLeft + timeLabel.compoundPaddingRight + margin)
                .also { timelineGuideOffset = it }
        }
        row.findViewById<Guideline>(guideId).setGuidelineBegin(offset)
    }

    class LessonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeStart: TextView = itemView.findViewById(R.id.time_start)
        private val timeEnd: TextView = itemView.findViewById(R.id.time_end)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val teacherLayout: View = itemView.findViewById(R.id.teacher_layout)
        private val teacherName: TextView = itemView.findViewById(R.id.teacher_text_view)
        private val locationLayout: View = itemView.findViewById(R.id.location_layout)
        private val locationRoom: TextView = itemView.findViewById(R.id.location_room)
        private val locationBuilding: TextView = itemView.findViewById(R.id.location_building)
        private val noteLayout: View = itemView.findViewById(R.id.note_layout)
        private val noteText: TextView = itemView.findViewById(R.id.note)
        private val card: LinearLayout = itemView.findViewById(R.id.card_container)
        private val timelineDot: ImageView = itemView.findViewById(R.id.timeline_dot)
        private val typeIndicator: ImageView = itemView.findViewById(R.id.type_indicator)
        private val typeLabel: TextView = itemView.findViewById(R.id.type)

        fun bind(item: ScheduleItem.LessonItem) {
            val lesson = item.lesson

            (card.layoutParams as? ViewGroup.MarginLayoutParams?)?.bottomMargin = if (item.isLastLesson) 0 else 16.dp

            title.text = lesson.subjectName.ifBlank {
                itemView.context.getString(R.string.schedule_unknown_subject)
            }
            timeStart.text = lesson.start.format(TIME_FORMATTER)
            timeEnd.text = lesson.end.format(TIME_FORMATTER)

            if (lesson.teacherFio != null) {
                teacherName.text = lesson.teacherFio
                teacherLayout.visibility = View.VISIBLE
            } else {
                teacherLayout.visibility = View.GONE
            }

            if (lesson.note != null) {
                noteText.text = lesson.note.trim()
                noteLayout.visibility = View.VISIBLE
            } else {
                noteLayout.visibility = View.GONE
            }

            if (lesson.hasLocation()) {
                locationRoom.text = lesson.room?.shortTitle(itemView.context) ?: ""
                locationBuilding.text = lesson.building?.shortTitle(
                    context = itemView.context,
                    maxLength = 10
                ) ?: ""
                locationLayout.visibility = View.VISIBLE
            } else {
                locationLayout.visibility = View.GONE
            }

            val typeColor = ContextCompat.getColor(itemView.context, lesson.typeId.colorRes())
            typeIndicator.imageTintList = ColorStateList.valueOf(typeColor)
            typeLabel.setText(lesson.typeId.nameRes())
            val context = itemView.context
            val color = context.color

            // Completion uses semantic text colors, not nested opacity that also
            // weakens metadata contrast or survives a recycled completed row.
            itemView.alpha = 1f
            card.alpha = 1f
            timeEnd.setTextColor(color.onSurfaceVariant)

            when (item.lessonState) {
                ScheduleItem.LessonState.CURRENT -> {
                    title.setTextColor(color.onSurface)

                    timelineDot.setColorFilter(color.primary)
                    timelineDot.scaleX = 1.3f
                    timelineDot.scaleY = 1.3f

                    timeStart.setTextColor(color.primary)
                }
                ScheduleItem.LessonState.COMPLETED -> {
                    title.setTextColor(color.onSurfaceVariant)

                    timelineDot.setColorFilter(color.outline)
                    timelineDot.scaleX = 0.8f
                    timelineDot.scaleY = 0.8f

                    timeStart.setTextColor(color.onSurfaceVariant)
                }
                ScheduleItem.LessonState.UPCOMING -> {
                    title.setTextColor(color.onSurface)

                    timelineDot.setColorFilter(color.outline)
                    timelineDot.scaleX = 1.0f
                    timelineDot.scaleY = 1.0f

                    timeStart.setTextColor(color.onSurface)
                }
            }
        }
    }

    class BreakViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val breakText: TextView = itemView.findViewById(R.id.break_text)

        fun bind(item: ScheduleItem.BreakItem) {
            breakText.text = itemView.context.getString(
                R.string.schedule_break_range,
                item.from.format(TIME_FORMATTER),
                item.to.format(TIME_FORMATTER)
            )
        }
    }

    class EmptyDayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(item: ScheduleItem.NoLessonsItem) {
        }
    }
}
