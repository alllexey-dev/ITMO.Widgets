package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItem
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItemKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetEntryPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking

class ScheduleWidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return Factory(applicationContext)
    }

    private class Factory(
        private val context: Context
    ) : RemoteViewsFactory {

        private val store = ScheduleWidgetEntryPoint.from(context).scheduleWidgetSnapshotStore()
        private var snapshot = ScheduleWidgetSnapshot.loading()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            snapshot = runBlocking { store.read() }
        }

        override fun getCount(): Int = snapshot.lessonList.size

        override fun getViewAt(position: Int): RemoteViews? {
            val item = snapshot.lessonList.getOrNull(position) ?: return null
            return when (item.kind) {
                ScheduleListWidgetItemKind.LESSON -> {
                    val lesson = item.lesson ?: return null
                    ScheduleWidgetRenderer.lessonListRow(
                        context = context,
                        lesson = lesson,
                        style = snapshot.lessonListStyle
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

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = VIEW_TYPE_COUNT

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = true

        override fun onDestroy() = Unit

        private companion object {
            const val VIEW_TYPE_COUNT = 8
            val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(
                "d MMMM",
                Locale.forLanguageTag("ru")
            )
        }
    }
}
