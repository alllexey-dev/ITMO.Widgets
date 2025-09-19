package me.alllexey123.itmowidgets.widgets

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.utils.ScheduleUtils


class LessonListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory? {
        return ViewsFactory(this.applicationContext, intent)
    }

    class ViewsFactory(val context: Context, val intent: Intent) : RemoteViewsFactory {

        var lessons: ArrayList<SingleLessonData> = ArrayList()

        override fun getCount(): Int {
            return lessons.size
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getLoadingView(): RemoteViews? {
            return null
        }

        override fun getViewAt(position: Int): RemoteViews? {
            val views = RemoteViews(context.packageName, R.layout.single_lesson_widget_dynamic)
            val data: SingleLessonData? = lessons[position]
            if (data == null) return null

            views.setTextViewText(R.id.title, data.subject)
            views.setTextViewText(R.id.teacher, data.teacher)
            views.setTextViewText(R.id.location_room, data.room)
            views.setTextViewText(R.id.location_building, data.building)
            views.setTextViewText(R.id.more_lessons_text, data.moreLessonsText)
            views.setViewVisibility(
                R.id.teacher_layout,
                if (data.hideTeacher) View.GONE else View.VISIBLE
            )
            views.setViewVisibility(
                R.id.location_layout,
                if (data.hideLocation) View.GONE else View.VISIBLE
            )
            views.setViewVisibility(
                R.id.time_layout,
                if (data.hideTime) View.GONE else View.VISIBLE
            )
            views.setViewVisibility(
                R.id.more_lessons_layout,
                if (data.hideMoreLessonsText) View.GONE else View.VISIBLE
            )

            views.setTextViewText(R.id.time, data.times)

            val colorId = ScheduleUtils.getWorkTypeColor(data.workTypeId)

            views.setInt(
                R.id.type_indicator, "setColorFilter",
                ContextCompat.getColor(context, colorId)
            )

            return views
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        override fun onCreate() {
            lessons = extractLessons(intent)
        }

        override fun onDataSetChanged() {
            lessons = extractLessons(intent)
        }

        override fun onDestroy() {

        }

        @Suppress("DEPRECATION")
        private fun extractLessons(intent: Intent): ArrayList<SingleLessonData> {
            val bundles = intent.getParcelableArrayListExtra<Bundle>(LessonListWidget.LESSON_LIST_EXTRA)
            return bundles?.map { b ->
                SingleLessonData(
                    subject = b.getString("subject", ""),
                    times = b.getString("times", ""),
                    teacher = b.getString("teacher", ""),
                    workTypeId = b.getInt("workTypeId", 0),
                    room = b.getString("room", ""),
                    building = b.getString("building", ""),
                    moreLessonsText = b.getString("moreLessonsText", ""),
                    hideTeacher = b.getBoolean("hideTeacher", false),
                    hideLocation = b.getBoolean("hideLocation", false),
                    hideTime = b.getBoolean("hideTime", false),
                    hideMoreLessonsText = b.getBoolean("hideMoreLessonsText", false)
                )
            }?.let { ArrayList(it) } ?: ArrayList()
        }


    }
}