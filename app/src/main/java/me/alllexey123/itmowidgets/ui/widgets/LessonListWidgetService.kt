package me.alllexey123.itmowidgets.ui.widgets

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.ui.widgets.data.LessonListWidgetData
import me.alllexey123.itmowidgets.ui.widgets.data.LessonListWidgetEntry
import me.alllexey123.itmowidgets.util.ScheduleUtils


class LessonListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ViewsFactory(applicationContext)
    }

    class ViewsFactory(private val context: Context) : RemoteViewsFactory {

        private val lessonListRepository =
            (context.applicationContext as ItmoWidgetsApp).appContainer.lessonListRepository

        private var data: LessonListWidgetData = LessonListWidgetData(listOf())

        override fun onCreate() {
        }

        override fun onDataSetChanged() {
            loadData()
        }

        private fun loadData() {
            runBlocking {
                data = lessonListRepository.data.first()
            }
        }

        override fun getCount(): Int = data.entries.size

        override fun getViewAt(position: Int): RemoteViews? {
            if (position >= data.entries.size) {
                return null
            }

            val entry = data.entries[position]

            val rv = RemoteViews(context.packageName, entry.layoutId)

            if (entry is LessonListWidgetEntry.LessonData) {
                rv.apply {
                    setTextViewText(R.id.title, entry.subject)
                    setTextViewText(R.id.teacher, entry.teacher)
                    setTextViewText(R.id.time, entry.times)

                    setTextViewText(R.id.title, entry.subject)
                    setTextViewText(R.id.teacher, entry.teacher)
                    val roomText = if (entry.building == null) entry.room else entry.room?.let { room -> "${room}, " }
                    setTextViewText(R.id.location_room, roomText)
                    setTextViewText(R.id.location_building, entry.building)
                    setTextViewText(R.id.time, entry.times)

                    setViewVisibility(R.id.teacher_layout, if (entry.teacher.isNullOrEmpty()) View.GONE else View.VISIBLE)
                    val hideLocation = roomText.isNullOrEmpty() && entry.building.isNullOrEmpty()
                    setViewVisibility(R.id.location_layout, if (hideLocation) View.GONE else View.VISIBLE)
                    setViewVisibility(R.id.time_layout, if (entry.times.isNullOrEmpty()) View.GONE else View.VISIBLE)

                    val color = ScheduleUtils.getWorkTypeColor(entry.workTypeId)
                    setInt(R.id.type_indicator, "setColorFilter", ContextCompat.getColor(context, color))
                }
            }

            val fillInIntent = Intent()
            rv.setOnClickFillInIntent(R.id.item_root, fillInIntent)

            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 9
        override fun hasStableIds(): Boolean = true
        override fun getItemId(position: Int): Long = position.toLong()
        override fun onDestroy() {}
    }
}
