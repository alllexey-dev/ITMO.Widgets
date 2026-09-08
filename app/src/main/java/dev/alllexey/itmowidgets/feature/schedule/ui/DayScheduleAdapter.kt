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
import dev.alllexey.itmowidgets.core.util.dp
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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

        holder.dayTitle.text = date.dayOfWeek
            .getDisplayName(TextStyle.FULL, RUSSIAN_LOCALE)
            .replaceFirstChar { it.uppercase(RUSSIAN_LOCALE) }
        holder.dayDate.text = date.format(DATE_FORMATTER)

        holder.numberOfLessons.text = if (lessons.isEmpty()) {
            context.getString(R.string.schedule_no_lessons)
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
                val currentEndTime = currentLesson.end
                val nextStartTime = nextLesson.start
                val breakDuration = Duration.between(currentEndTime, nextStartTime)
                if (breakDuration > BIG_BREAK_THRESHOLD) {
                    processedList.add(ScheduleItem.BreakItem(currentEndTime, nextStartTime))
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
        private val RUSSIAN_LOCALE = Locale.forLanguageTag("ru")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN_LOCALE)
        private val ScheduleDiffCallback = object : DiffUtil.ItemCallback<DaySchedule>() {
            override fun areItemsTheSame(oldItem: DaySchedule, newItem: DaySchedule) = oldItem.date == newItem.date
            override fun areContentsTheSame(oldItem: DaySchedule, newItem: DaySchedule) = oldItem == newItem
        }
    }
}
