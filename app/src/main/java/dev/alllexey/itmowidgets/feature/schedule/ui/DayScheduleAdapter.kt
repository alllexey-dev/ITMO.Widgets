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
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.dp
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDayUi
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDaySummary
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDisplayDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.buildScheduleListUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.isoDayNumber

/**
 * Binds [ScheduleDayUi] built by [buildScheduleListUi]. The list stays keyed by [ScheduleDisplayDay] so content
 * changes diff by date; time only recomputes the model ([updateLessonStates]).
 */
class DayScheduleAdapter(
    private val timeProvider: AcademicTimeProvider,
    private val onLessonClick: (Lesson, LocalDate) -> Unit = { _, _ -> },
    private val onPendingClick: (PendingSportBooking) -> Unit = {}
) :
    ListAdapter<ScheduleDisplayDay, DayScheduleAdapter.DayViewHolder>(ScheduleDiffCallback) {

    private var modelDays = emptyList<ScheduleDisplayDay>()
    private var model = emptyList<ScheduleDayUi>()

    override fun onCurrentListChanged(
        previousList: List<ScheduleDisplayDay>,
        currentList: List<ScheduleDisplayDay>
    ) {
        val previousByDate = model.associateBy { it.date }
        updateModel(currentList)
        model.forEachIndexed { index, day ->
            val previous = previousByDate[day.date]
            if (previous != null && previous != day) {
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
        // Also support a first bind before ListAdapter's list-change callback.
        // Keep the committed model intact for its old/new comparison.
        val day = if (modelDays === currentList) model[position] else buildModel(currentList)[position]
        val context = holder.itemView.context

        holder.dayTitle.text = DateTexts.Names.DAYS_FULL.names[day.title.weekday.isoDayNumber - 1]
            .replaceFirstChar { it.uppercaseChar() }
        holder.dayDate.text = day.title.date.format(DateTexts.DAY_MONTH)

        holder.numberOfLessons.text = when (val summary = day.summary) {
            is ScheduleDaySummary.Lessons -> context.resources.getQuantityString(
                R.plurals.schedule_lesson_count,
                summary.count,
                summary.count
            )
            ScheduleDaySummary.AutoSignOnly -> context.getString(R.string.schedule_auto_sign_label)
            ScheduleDaySummary.NoLessons -> context.getString(R.string.schedule_no_lessons)
        }

        holder.card.setCardBackgroundColor(
            context.color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLow)
        )
        holder.card.elevation = 0f
        if (day.isToday) {
            holder.card.strokeWidth = 2.dp
            holder.card.strokeColor = context.color.primary
            holder.dayTitle.setTextColor(context.color.primary)
            holder.numberOfLessons.setBackgroundResource(R.drawable.shape_pill_outline_selected)
        } else {
            holder.card.strokeWidth = 0
            holder.dayTitle.setTextColor(context.color.onSurface)
            holder.numberOfLessons.setBackgroundResource(R.drawable.shape_pill_outline)
        }

        // Lesson rows remain opaque so the fade is applied only once. Always
        // reset it when a holder is reused.
        holder.itemRoot.alpha = if (day.isPast) PAST_DAY_ALPHA else 1f

        val lessonAdapter = LessonAdapter(day.rows, { lesson -> onLessonClick(lesson, day.date) }, onPendingClick)
        // Days are recycled by the outer list. A day's bounded rows must all
        // contribute their natural height; a nested wrap-content RecyclerView
        // can stop measuring at the viewport and silently hide large-font rows.
        holder.lessonList.removeAllViews()
        day.rows.indices.forEach { index ->
            val row = lessonAdapter.onCreateViewHolder(holder.lessonList, lessonAdapter.getItemViewType(index))
            lessonAdapter.onBindViewHolder(row, index)
            holder.lessonList.addView(row.itemView)
        }
    }

    /** Called by the minute ticker: rebinds only the days whose time states or today/past flags changed. */
    fun updateLessonStates() {
        val previous = model
        updateModel(currentList)
        model.forEachIndexed { index, day ->
            if (previous.getOrNull(index) != day) notifyItemChanged(index)
        }
    }

    private fun updateModel(days: List<ScheduleDisplayDay>) {
        modelDays = days
        model = buildModel(days)
    }

    private fun buildModel(days: List<ScheduleDisplayDay>) =
        buildScheduleListUi(days, timeProvider.localNow(), timeProvider.timeZone)

    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.day_card)
        val itemRoot: LinearLayout = itemView.findViewById(R.id.day_card_root)
        val dayTitle: TextView = itemView.findViewById(R.id.day_title)
        val numberOfLessons: TextView = itemView.findViewById(R.id.number_of_lessons)
        val dayDate: TextView = itemView.findViewById(R.id.day_date)
        val lessonList: LinearLayout = itemView.findViewById(R.id.lesson_list)
    }

    companion object {
        private const val PAST_DAY_ALPHA = 0.72f
        private val ScheduleDiffCallback = object : DiffUtil.ItemCallback<ScheduleDisplayDay>() {
            override fun areItemsTheSame(oldItem: ScheduleDisplayDay, newItem: ScheduleDisplayDay) = oldItem.date == newItem.date
            override fun areContentsTheSame(oldItem: ScheduleDisplayDay, newItem: ScheduleDisplayDay) = oldItem == newItem
        }
    }
}
