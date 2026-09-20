package dev.alllexey.itmowidgets.feature.schedule.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.core.util.dp
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDisplayDay
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import java.time.LocalDate
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class DayScheduleAdapter(
    private val timeProvider: AcademicTimeProvider,
    private val onLessonClick: (Lesson, LocalDate) -> Unit = { _, _ -> }
) :
    ListAdapter<ScheduleDisplayDay, DayScheduleAdapter.DayViewHolder>(ScheduleDiffCallback) {

    private var timelineDays = emptyList<ScheduleDisplayDay>()
    private var timelineStates = emptyList<List<ScheduleItem.LessonState>>()
    private var renderedToday = timeProvider.today()

    override fun onCurrentListChanged(
        previousList: List<ScheduleDisplayDay>,
        currentList: List<ScheduleDisplayDay>
    ) {
        val previousStatesByDate = timelineDays.mapIndexed { index, day ->
            day.date to timelineStates[index]
        }.toMap()
        updateTimeline(currentList)
        currentList.forEachIndexed { index, day ->
            val previousStates = previousStatesByDate[day.date]
            if (previousStates != null && previousStates != timelineStates[index]) {
                // An inserted earlier lesson can move NEXT off an unchanged day,
                // which the day-content DiffUtil comparison cannot detect.
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_schedule, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val daySchedule = getItem(position)
        val date = daySchedule.date
        val lessons = daySchedule.officialDay?.lessons.orEmpty()
        val context = holder.itemView.context

        holder.dayTitle.text = date.dayOfWeek
            .getDisplayName(TextStyle.FULL, RUSSIAN_LOCALE)
            .replaceFirstChar { it.uppercase(RUSSIAN_LOCALE) }
        holder.dayDate.text = date.format(DATE_FORMATTER)

        holder.numberOfLessons.text = if (lessons.isEmpty()) {
            context.getString(if (daySchedule.pendingSport.isEmpty()) R.string.schedule_no_lessons else R.string.schedule_auto_sign_label)
        } else {
            context.resources.getQuantityString(
                R.plurals.schedule_lesson_count,
                lessons.size,
                lessons.size
            )
        }

        val today = timeProvider.today()
        val isToday = date.equals(today)

        if (isToday) {
            holder.card.strokeWidth = 2.dp
            holder.card.strokeColor = context.color.primary
            holder.card.setCardBackgroundColor(
                context.color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLow)
            )
            holder.dayTitle.setTextColor(context.color.primary)
            holder.card.elevation = 0f
            holder.numberOfLessons.setBackgroundResource(R.drawable.shape_pill_outline_selected)
        } else {
            holder.card.strokeWidth = 0
            holder.card.setCardBackgroundColor(
                context.color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLow)
            )
            holder.dayTitle.setTextColor(context.color.onSurface)
            holder.card.elevation = 0f
            holder.numberOfLessons.setBackgroundResource(R.drawable.shape_pill_outline)
        }

        // Past days keep the established fade; lesson rows remain opaque so it
        // is applied only once. Always reset it when a holder is reused.
        holder.itemRoot.alpha = if (date.isBefore(today)) 0.72f else 1f

        // Also support a first bind before ListAdapter's list-change callback.
        // Keep the committed snapshot intact for its old/new marker comparison.
        val states = if (timelineDays === currentList) timelineStates
        else resolveScheduleTimeline(currentList, timeProvider.now().toLocalDateTime())
        val processed = processLessonsWithBreaks(lessons, states[position], daySchedule.pendingSport)
        val lessonAdapter = LessonAdapter(processed) { lesson -> onLessonClick(lesson, date) }
        // Days are recycled by the outer list. A day's bounded rows must all
        // contribute their natural height; a nested wrap-content RecyclerView
        // can stop measuring at the viewport and silently hide large-font rows.
        holder.lessonList.removeAllViews()
        processed.indices.forEach { index ->
            val row = lessonAdapter.onCreateViewHolder(holder.lessonList, lessonAdapter.getItemViewType(index))
            lessonAdapter.onBindViewHolder(row, index)
            holder.lessonList.addView(row.itemView)
        }
    }

    fun updateLessonStates() {
        val previousStates = timelineStates
        val previousToday = renderedToday
        updateTimeline(currentList)
        renderedToday = timeProvider.today()
        if (previousToday != renderedToday) {
            // Date-card emphasis and past-day alpha also change at midnight.
            notifyItemRangeChanged(0, itemCount)
        } else {
            timelineStates.forEachIndexed { index, states ->
                if (previousStates.getOrNull(index) != states) notifyItemChanged(index)
            }
        }
    }

    private fun updateTimeline(days: List<ScheduleDisplayDay>) {
        timelineDays = days
        timelineStates = resolveScheduleTimeline(days, timeProvider.now().toLocalDateTime())
    }

    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.day_card)
        val itemRoot: LinearLayout = itemView.findViewById(R.id.day_card_root)
        val dayTitle: TextView = itemView.findViewById(R.id.day_title)
        val numberOfLessons: TextView = itemView.findViewById(R.id.number_of_lessons)
        val dayDate: TextView = itemView.findViewById(R.id.day_date)
        val lessonList: LinearLayout = itemView.findViewById(R.id.lesson_list)
    }

    private fun processLessonsWithBreaks(
        lessons: List<Lesson>,
        states: List<ScheduleItem.LessonState>,
        pending: List<PendingSportBooking>
    ): List<ScheduleItem> {
        val processedList = mutableListOf<ScheduleItem>()
        val sortedLessons = lessons.withIndex().sortedBy { it.value.start }

        sortedLessons.forEachIndexed { index, indexedLesson ->
            val currentLesson = indexedLesson.value
            processedList.add(ScheduleItem.LessonItem(currentLesson, states[indexedLesson.index], index == sortedLessons.size - 1))

            if (index < sortedLessons.size - 1) {
                val nextLesson = sortedLessons[index + 1].value
                val currentEndTime = currentLesson.end
                val nextStartTime = nextLesson.start
                val breakDuration = Duration.between(currentEndTime, nextStartTime)
                val overlapsPending = pending.any {
                    it.start.toLocalTime() < nextStartTime && it.end.toLocalTime() > currentEndTime
                }
                if (breakDuration > BIG_BREAK_THRESHOLD && !overlapsPending) {
                    processedList.add(ScheduleItem.BreakItem(currentEndTime, nextStartTime))
                }
            }
        }

        processedList += pending.map { ScheduleItem.PendingSportItem(it, isLast = false) }
        if (processedList.isEmpty()) {
            return listOf(ScheduleItem.NoLessonsItem(ScheduleItem.LessonState.COMPLETED))
        }

        return processedList.sortedBy { item -> when (item) {
            is ScheduleItem.LessonItem -> item.lesson.start
            is ScheduleItem.PendingSportItem -> item.booking.start.toLocalTime()
            is ScheduleItem.BreakItem -> item.from
            is ScheduleItem.NoLessonsItem -> LocalTime.MIN
        } }.mapIndexed { index, item -> when (item) {
            is ScheduleItem.LessonItem -> item.copy(isLastLesson = index == processedList.lastIndex)
            is ScheduleItem.PendingSportItem -> item.copy(isLast = index == processedList.lastIndex)
            else -> item
        } }
    }

    companion object {
        private val BIG_BREAK_THRESHOLD = Duration.ofMinutes(60)
        private val RUSSIAN_LOCALE = Locale.forLanguageTag("ru")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN_LOCALE)
        private val ScheduleDiffCallback = object : DiffUtil.ItemCallback<ScheduleDisplayDay>() {
            override fun areItemsTheSame(oldItem: ScheduleDisplayDay, newItem: ScheduleDisplayDay) = oldItem.date == newItem.date
            override fun areContentsTheSame(oldItem: ScheduleDisplayDay, newItem: ScheduleDisplayDay) = oldItem == newItem
        }
    }
}
