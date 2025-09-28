package me.alllexey123.itmowidgets.widgets

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.utils.ScheduleUtils


class LessonListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ViewsFactory(applicationContext)
    }

    class ViewsFactory(private val context: Context) : RemoteViewsFactory {

        private var lessons: List<SingleLessonData> = emptyList()
        private var rowLayoutId: Int = R.layout.single_lesson_widget_variant
        private var bonusLayoutId: Int = R.layout.lesson_list_empty

        override fun onCreate() {
            loadData()
        }

        override fun onDataSetChanged() {
            loadData()
        }

        private fun loadData() {
            lessons = LessonRepository.getLessons()
            rowLayoutId = LessonRepository.rowLayoutId
            bonusLayoutId = LessonRepository.bonusLayoutId
        }

        override fun getCount(): Int = lessons.size + 1

        override fun getViewAt(position: Int): RemoteViews? {
            var rv: RemoteViews
            if (lessons.isEmpty()) {
                rv = RemoteViews(context.packageName, bonusLayoutId)
            } else if (position >= lessons.size) {
                rv = RemoteViews(context.packageName, R.layout.lesson_list_end)
            } else {

                val data = lessons[position]
                val views = RemoteViews(context.packageName, rowLayoutId)

                views.setTextViewText(R.id.title, data.subject)
                views.setTextViewText(R.id.teacher, data.teacher)
                views.setTextViewText(R.id.location_room, data.room)
                views.setTextViewText(R.id.location_building, data.building)
                views.setTextViewText(R.id.more_lessons_text, data.moreLessonsText)

                views.setViewVisibility(R.id.teacher_layout, if (data.hideTeacher) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.location_layout, if (data.hideLocation) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.time_layout, if (data.hideTime) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.more_lessons_layout, if (data.hideMoreLessonsText) View.GONE else View.VISIBLE)

                views.setTextViewText(R.id.time, data.times)

                val colorId = ScheduleUtils.getWorkTypeColor(data.workTypeId)
                views.setInt(R.id.type_indicator, "setColorFilter", ContextCompat.getColor(context, colorId))

                rv = views
            }
            val fillInIntent = Intent()
            rv.setOnClickFillInIntent(R.id.item_root, fillInIntent)

            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 2
        override fun hasStableIds(): Boolean = true
        override fun getItemId(position: Int): Long = position.toLong()
        override fun onDestroy() {}
    }
}
