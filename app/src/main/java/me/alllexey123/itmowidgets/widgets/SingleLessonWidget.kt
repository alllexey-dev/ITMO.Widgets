package me.alllexey123.itmowidgets.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import api.myitmo.model.Lesson
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.WidgetUpdateWorker
import me.alllexey123.itmowidgets.utils.ScheduleUtils

class SingleLessonWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WidgetUpdateWorker.Companion.WIDGET_UPDATE_WORK_NAME)
    }

    companion object {
        internal fun widgetData(lesson: Lesson): SingleLessonWidgetData {
            val startTime = lesson.timeStart
            val endTime = lesson.timeEnd
            val building = lesson.building
            val shortBuilding = if (building == null) "" else ScheduleUtils.shortenBuildingName(building)
            val room = if (lesson.room == null) "нет кабинета" else lesson.room + ", "

            return SingleLessonWidgetData(
                subject = lesson.subject ?: "Неизвестная дисциплина",
                times = "$startTime - $endTime",
                teacher = lesson.teacherName ?: "",
                workTypeId = lesson.workTypeId,
                room = room,
                building = shortBuilding,
                hideTeacher = lesson.teacherName == null,
                hideLocation = false,
                hideTime = false
            )

        }

        fun noLessonsWidgetData(): SingleLessonWidgetData {
            return SingleLessonWidgetData(
                subject = "Сегодня пар нет!",
                workTypeId = -1,
                hideTeacher = true, hideLocation = true, hideTime = true
            )
        }

        internal fun noLeftLessonsWidgetData(): SingleLessonWidgetData {
            return SingleLessonWidgetData(
                subject = "Сегодня больше нет пар!",
                workTypeId = -1,
                hideTeacher = true, hideLocation = true, hideTime = true
            )
        }

        fun errorLessonWidgetData(): SingleLessonWidgetData {
            return SingleLessonWidgetData(
                subject = "Ошибка при получении данных",
                workTypeId = 0,
                hideTeacher = true, hideLocation = true, hideTime = true
            )
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            data: SingleLessonWidgetData
        ) {
            val views = RemoteViews(context.packageName, R.layout.single_lesson_widget)

            views.setTextViewText(R.id.title, data.subject)
            views.setTextViewText(R.id.teacher, data.teacher)
            views.setTextViewText(R.id.location_room, data.room)
            views.setTextViewText(R.id.location_building, data.building)
            views.setViewVisibility(R.id.teacher_layout, if (data.hideTeacher) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.location_layout, if (data.hideLocation) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.time_layout, if (data.hideTime) View.GONE else View.VISIBLE)

            views.setTextViewText(R.id.time, data.times)

            val colorId = ScheduleUtils.getWorkTypeColor(data.workTypeId)

            views.setInt(
                R.id.divider, "setColorFilter",
                ContextCompat.getColor(context, colorId)
            )

            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }
    }
}

class SingleLessonWidgetData(
    val subject: String = "", val times: String = "", val teacher: String = "",
    val workTypeId: Int = 0, val room: String = "", val building: String = "",
    val hideTeacher: Boolean, val hideLocation: Boolean, val hideTime: Boolean
)