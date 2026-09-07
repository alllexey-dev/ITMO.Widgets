package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.widget.RemoteViews
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItem
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItemKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleListRowRenderer(private val context: Context) {
    fun render(item: ScheduleListWidgetItem, style: LessonStyle): RemoteViews? {
        return when (item.kind) {
            ScheduleListWidgetItemKind.LESSON -> {
                val lesson = item.lesson ?: return null
                ScheduleWidgetRenderer.lessonListRow(
                    context = context,
                    lesson = lesson,
                    style = style
                )
            }

            ScheduleListWidgetItemKind.HEADER -> header(item)
            ScheduleListWidgetItemKind.EMPTY_TODAY -> message(
                R.layout.item_lesson_list_empty,
                R.id.no_lessons,
                R.string.schedule_widget_empty_today
            )

            ScheduleListWidgetItemKind.EMPTY_TODAY_AND_TOMORROW -> message(
                R.layout.item_lesson_list_empty,
                R.id.no_lessons,
                R.string.schedule_widget_empty_today_and_tomorrow
            )

            ScheduleListWidgetItemKind.NO_MORE_TODAY -> message(
                R.layout.item_lesson_list_no_more,
                R.id.no_more_lessons,
                R.string.schedule_widget_no_more_today
            )

            ScheduleListWidgetItemKind.END -> message(
                R.layout.item_lesson_list_end,
                R.id.end_marker,
                if (item.tomorrow) {
                    R.string.schedule_widget_end_tomorrow
                } else {
                    R.string.schedule_widget_end_today
                }
            )

            ScheduleListWidgetItemKind.ERROR -> message(
                R.layout.item_lesson_list_error,
                R.id.empty_view,
                R.string.schedule_widget_error
            )

            ScheduleListWidgetItemKind.SIGNED_OUT -> message(
                R.layout.item_lesson_list_error,
                R.id.empty_view,
                R.string.schedule_widget_signed_out
            )

            ScheduleListWidgetItemKind.LOADING -> message(
                R.layout.item_lesson_list_updating,
                R.id.empty_view,
                R.string.schedule_widget_loading
            )
        }
    }

    private fun header(item: ScheduleListWidgetItem): RemoteViews {
        val date = item.dateIso?.let(LocalDate::parse)
        val formattedDate = date?.format(DATE_FORMATTER).orEmpty()
        val day = context.getString(
            if (item.tomorrow) {
                R.string.schedule_widget_tomorrow
            } else {
                R.string.schedule_widget_today
            }
        )
        return ScheduleWidgetRenderer.listMessageRow(
            context = context,
            layoutId = R.layout.item_lesson_list_day_title,
            textViewId = R.id.day_title,
            text = context.getString(
                R.string.schedule_widget_day_header,
                day,
                formattedDate
            )
        )
    }

    private fun message(layoutId: Int, textViewId: Int, textId: Int): RemoteViews {
        return ScheduleWidgetRenderer.listMessageRow(
            context = context,
            layoutId = layoutId,
            textViewId = textViewId,
            text = context.getString(textId)
        )
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
    }
}
