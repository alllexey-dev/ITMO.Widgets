package me.alllexey123.itmowidgets.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.net.toUri
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.workers.LessonWidgetUpdateWorker

class LessonListWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        LessonWidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
    }

    companion object {
        const val LESSON_LIST_EXTRA = "lesson_list_extra"

        @Suppress("DEPRECATION")
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            lessons: ArrayList<SingleLessonData>,
            layoutId: Int,
            rowLayoutId: Int,
        ) {
            println("PUTTING $rowLayoutId")
            val intent = Intent(context, LessonListWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                val list = ArrayList(lessons.map {
                    Bundle().apply {
                        putString("subject", it.subject)
                        putString("times", it.times)
                        putString("teacher", it.teacher)
                        putInt("workTypeId", it.workTypeId)
                        putString("room", it.room)
                        putString("building", it.building)
                        putString("moreLessonsText", it.moreLessonsText)
                        putBoolean("hideTeacher", it.hideTeacher)
                        putBoolean("hideLocation", it.hideLocation)
                        putBoolean("hideTime", it.hideTime)
                        putBoolean("hideMoreLessonsText", it.hideMoreLessonsText)
                    }
                })

                list.add(Bundle().apply {
                    putInt("rowLayoutId", rowLayoutId)
                })

                putParcelableArrayListExtra(
                    LESSON_LIST_EXTRA,
                    list
                )

                data = ("${toUri(Intent.URI_INTENT_SCHEME)}-${lessons.hashCode()}-${rowLayoutId}").toUri()

            }

            val views = RemoteViews(context.packageName, layoutId)

            views.setRemoteAdapter(R.id.lesson_list, intent)

//            views.setEmptyView(
//                R.id.lesson_list,
//                R.id.empty_view
//

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lesson_list)
        }

    }
}