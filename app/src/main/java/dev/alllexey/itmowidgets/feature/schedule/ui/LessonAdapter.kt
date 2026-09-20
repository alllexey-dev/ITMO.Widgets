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
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

class LessonAdapter(
    private val scheduleList: List<ScheduleItem>,
    /** Only real lessons open details; pending sport rows stay inert. */
    private val onLessonClick: (Lesson) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_LESSON = 1
        private const val VIEW_TYPE_BREAK = 2
        private const val VIEW_TYPE_NO_LESSONS = 3
        private const val VIEW_TYPE_PENDING_SPORT = 4
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    }

    private var timelineGuideOffset: Int? = null

    override fun getItemViewType(position: Int): Int {
        return when (scheduleList[position]) {
            is ScheduleItem.LessonItem -> VIEW_TYPE_LESSON
            is ScheduleItem.BreakItem -> VIEW_TYPE_BREAK
            is ScheduleItem.NoLessonsItem -> VIEW_TYPE_NO_LESSONS
            is ScheduleItem.PendingSportItem -> VIEW_TYPE_PENDING_SPORT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_LESSON -> LessonViewHolder(inflater.inflate(R.layout.item_schedule_lesson, parent, false))
            VIEW_TYPE_BREAK -> BreakViewHolder(inflater.inflate(R.layout.item_schedule_break, parent, false))
            VIEW_TYPE_NO_LESSONS -> EmptyDayViewHolder(inflater.inflate(R.layout.item_schedule_empty, parent, false))
            VIEW_TYPE_PENDING_SPORT -> PendingSportViewHolder(inflater.inflate(R.layout.item_schedule_lesson, parent, false).apply {
                id = R.id.pending_sport_root
            })
            else -> throw IllegalArgumentException("Invalid type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = scheduleList[position]) {
            is ScheduleItem.LessonItem -> {
                updateTimelineGuide(holder.itemView, R.id.timeline_guide)
                (holder as LessonViewHolder).bind(item, onLessonClick)
            }
            is ScheduleItem.BreakItem -> {
                updateTimelineGuide(holder.itemView, R.id.timeline_guide_break)
                (holder as BreakViewHolder).bind(item)
            }
            is ScheduleItem.NoLessonsItem -> (holder as EmptyDayViewHolder).bind(item)
            is ScheduleItem.PendingSportItem -> {
                updateTimelineGuide(holder.itemView, R.id.timeline_guide)
                (holder as PendingSportViewHolder).bind(item)
            }
        }
    }

    override fun getItemCount() = scheduleList.size

    private fun updateTimelineGuide(row: View, guideId: Int) {
        val offset = timelineGuideOffset ?: run {
            val timeLabel = row.findViewById<TextView>(R.id.time_start)
                ?: LayoutInflater.from(row.context).inflate(R.layout.item_schedule_lesson, FrameLayout(row.context), false)
                    .findViewById<TextView>(R.id.time_start)
            val widestTime = scheduleList.flatMap { item -> when (item) {
                is ScheduleItem.LessonItem -> listOf(item.lesson.start, item.lesson.end)
                is ScheduleItem.PendingSportItem -> listOf(item.booking.start.toLocalTime(), item.booking.end.toLocalTime())
                else -> emptyList()
            } }
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

        fun bind(item: ScheduleItem.LessonItem, onClick: (Lesson) -> Unit) {
            val lesson = item.lesson
            card.setOnClickListener { onClick(lesson) }
            card.isFocusable = true

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

                    timelineDot.renderTimelineMarker(ScheduleTimelineMarker.CURRENT)
                    timeStart.setTextColor(color.primary)
                }
                ScheduleItem.LessonState.NEXT -> {
                    title.setTextColor(color.onSurface)
                    timelineDot.renderTimelineMarker(ScheduleTimelineMarker.NEXT)
                    timeStart.setTextColor(color.onSurface)
                }
                ScheduleItem.LessonState.COMPLETED -> {
                    title.setTextColor(color.onSurfaceVariant)

                    timelineDot.renderTimelineMarker(ScheduleTimelineMarker.COMPLETED)
                    timeStart.setTextColor(color.onSurfaceVariant)
                }
                ScheduleItem.LessonState.UPCOMING -> {
                    title.setTextColor(color.onSurface)

                    timelineDot.renderTimelineMarker(ScheduleTimelineMarker.UPCOMING)
                    timeStart.setTextColor(color.onSurface)
                }
            }
        }
    }

    class PendingSportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val start: TextView = itemView.findViewById(R.id.time_start)
        private val end: TextView = itemView.findViewById(R.id.time_end)
        private val status: TextView = itemView.findViewById(R.id.type)
        private val teacher: TextView = itemView.findViewById(R.id.teacher_text_view)
        private val location: TextView = itemView.findViewById(R.id.location_room)
        private val timelineDot: ImageView = itemView.findViewById(R.id.timeline_dot)
        private val typeIndicator: ImageView = itemView.findViewById(R.id.type_indicator)
        private val content: View = itemView.findViewById(R.id.card_container)

        fun bind(item: ScheduleItem.PendingSportItem) {
            val booking = item.booking
            // A pending booking has no details sheet; a recycled lesson row must not keep its click.
            content.setOnClickListener(null)
            content.isClickable = false
            content.isFocusable = false
            title.text = booking.sectionName
            start.text = booking.start.format(TIME_FORMATTER)
            end.text = booking.end.format(TIME_FORMATTER)
            title.setTextColor(itemView.context.color.onSurface)
            start.setTextColor(itemView.context.color.onSurface)
            end.setTextColor(itemView.context.color.onSurfaceVariant)
            timelineDot.renderTimelineMarker(ScheduleTimelineMarker.AUTO_SIGN)
            typeIndicator.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.lesson_type_sport))
            status.setText(if (booking.isPrediction) R.string.schedule_auto_sign_prediction else R.string.schedule_auto_sign_waiting)
            status.contentDescription = itemView.context.getString(
                if (booking.isPrediction) R.string.schedule_auto_sign_prediction_description else R.string.schedule_auto_sign_waiting_description
            )
            teacher.text = booking.teacherFio
            itemView.findViewById<View>(R.id.teacher_layout).visibility = if (booking.teacherFio.isBlank()) View.GONE else View.VISIBLE
            location.text = booking.roomName
            itemView.findViewById<View>(R.id.location_layout).visibility = if (booking.roomName.isBlank()) View.GONE else View.VISIBLE
            itemView.findViewById<View>(R.id.location_building).visibility = View.GONE
            itemView.findViewById<View>(R.id.note_layout).visibility = View.GONE
            (content.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = if (item.isLast) 0 else 16.dp
            itemView.alpha = 1f
            content.alpha = 1f
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
