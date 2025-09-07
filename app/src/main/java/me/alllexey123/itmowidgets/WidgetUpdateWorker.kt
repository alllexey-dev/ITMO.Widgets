package me.alllexey123.itmowidgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import api.myitmo.model.Lesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val widgetProvider = ComponentName(appContext, LessonWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetProvider)

        if (appWidgetIds.isEmpty()) {
            return Result.success()
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        prefs.edit {
            putLong("last_update_timestamp", System.currentTimeMillis())
        }

        if (MyItmoProvider.myItmoSingleton == null) {
            val myItmo = MyItmoProvider.createMyItmo(prefs)
            MyItmoProvider.myItmoSingleton = myItmo
        }

        val myItmo = MyItmoProvider.myItmoSingleton

        val lessonData: LessonData = try {
            val currentDateTime = LocalDateTime.now()
            val currentDate = LocalDate.now()

            val response = myItmo!!.api().getPersonalSchedule(currentDate, currentDate)
                .execute().body()

            if (response?.data != null && response.data.isNotEmpty() && response.data[0].lessons != null && response.data[0].lessons.isNotEmpty()) {
                val lessons = response.data[0].lessons
                var targetLesson: Lesson? = null

                val currTime = DateTimeFormatter.ofPattern("HH:mm").format(currentDateTime)
                for (lesson in lessons) {
                    if (lesson.timeEnd > currTime) {
                        targetLesson = lesson
                        break
                    }
                }

                if (targetLesson != null) {
                    val startTime = targetLesson.timeStart
                    val endTime = targetLesson.timeEnd
                    val building = targetLesson.building
                    val shortBuilding = if (building == null) "" else getShortBuildingName(building)
                    val room = if (targetLesson.room == null) "нет кабинета" else targetLesson.room + ", "

                    LessonData(
                        targetLesson.subject ?: "Неизвестная дисциплина",
                        "$startTime - $endTime",
                        targetLesson.teacherName ?: "Неизвестный преподаватель",
                        targetLesson.workTypeId,
                        room,
                        shortBuilding,
                        hideTeacher = targetLesson.teacherName == null, hideLocation = false, hideTime = false
                    )
                } else {
                    val lastLessonEndTime = lessons.lastOrNull()?.timeEnd ?: "00:00"
                    LessonData(
                        "Сегодня больше нет пар!",
                        "$lastLessonEndTime - 23:59",
                        "нет",
                        -1,
                        "нет",
                        "",
                        hideTeacher = true, hideLocation = true, hideTime = true
                    )
                }
            } else {
                LessonData(
                    "Сегодня пар нет!",
                    "00:00 - 23:59",
                    "нет",
                    -1,
                    "нет",
                    "",
                    hideTeacher = true, hideLocation = true, hideTime = true
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LessonData(
                "Ошибка при получении данных",
                "??:?? - ??:??",
                "...",
                0,
                "...",
                "",
                hideTeacher = true, hideLocation = true, hideTime = true
            )
        }

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(appContext, appWidgetManager, appWidgetId, lessonData)
        }

        scheduleNextUpdate(appContext)

        return Result.success()
    }

    companion object {
        const val WIDGET_UPDATE_WORK_NAME = "me.alllexey123.itmowidgets.LessonWidgetUpdate"

        fun enqueueImmediateUpdate(context: Context) {
            val immediateWorkRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WIDGET_UPDATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateWorkRequest
            )
        }

        fun enqueueImmediateUpdateIfNot(context: Context) {
            val immediateWorkRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WIDGET_UPDATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                immediateWorkRequest
            )
        }


        fun scheduleNextUpdate(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val duration = prefs.getString("update_interval", "7")?.toLongOrNull() ?: 7L
            val updateWorkRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInitialDelay(duration, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WIDGET_UPDATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                updateWorkRequest
            )
        }
    }
}