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

        var rowLayoutId: Int = R.layout.single_lesson_widget_variant

        var bonusLayoutId: Int = R.layout.lesson_list_empty

        override fun getCount(): Int {
            return lessons.size + 1
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getLoadingView(): RemoteViews? {
            return null
        }

        override fun getViewAt(position: Int): RemoteViews? {
            if (lessons.isEmpty()) {
                return RemoteViews(context.packageName, bonusLayoutId)
            } else {
                if (position >= lessons.size) {
                    return RemoteViews(context.packageName, R.layout.lesson_list_end)
                }
                val views = RemoteViews(context.packageName, rowLayoutId)
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
        }

        override fun getViewTypeCount(): Int {
            return 2
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        override fun onCreate() {
            extractData(intent)
        }

        override fun onDataSetChanged() {
            extractData(intent)
        }

        override fun onDestroy() {

        }

        @Suppress("DEPRECATION")
        private fun extractData(intent: Intent) {
            val bundles =
                intent.getParcelableArrayListExtra<Bundle>(LessonListWidget.LESSON_LIST_EXTRA)
            lessons = bundles?.subList(0, bundles.size - 1)?.map { b ->
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

            val settingsBundle = bundles?.get(bundles.size - 1)
            rowLayoutId = settingsBundle?.getInt("rowLayoutId") ?: R.layout.single_lesson_widget_variant
            bonusLayoutId = settingsBundle?.getInt("bonusLayoutId") ?: R.layout.lesson_list_empty
        }


    }
}