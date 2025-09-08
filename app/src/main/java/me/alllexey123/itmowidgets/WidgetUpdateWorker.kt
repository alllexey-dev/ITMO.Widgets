package me.alllexey123.itmowidgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.alllexey123.itmowidgets.providers.ScheduleProvider
import me.alllexey123.itmowidgets.providers.StorageProvider
import me.alllexey123.itmowidgets.widgets.SingleLessonWidget
import me.alllexey123.itmowidgets.widgets.SingleLessonWidgetData
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val widgetProvider = ComponentName(appContext, SingleLessonWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetProvider)

        if (appWidgetIds.isEmpty()) {
            return Result.success()
        }

        val storage = StorageProvider.getStorage(appContext)
        storage.setLastUpdateTimestamp(System.currentTimeMillis())

        val singleLessonWidgetData: SingleLessonWidgetData = try {
            val currentDateTime = LocalDateTime.now()
            val currentDate = LocalDate.now().plusDays(1)

            val daySchedule = ScheduleProvider.getDaySchedule(appContext, currentDate)
            val lessons = daySchedule.lessons

            if (lessons == null || lessons.isEmpty()) {
                SingleLessonWidget.noLessonsWidgetData()
            }

            val targetLesson = ScheduleProvider.findCurrentOrNextLesson(lessons, currentDateTime)

            if (targetLesson != null) {
                SingleLessonWidget.widgetData(targetLesson)
            } else {
                SingleLessonWidget.noLeftLessonsWidgetData()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SingleLessonWidget.errorLessonWidgetData()
        }

        for (appWidgetId in appWidgetIds) {
            SingleLessonWidget.updateAppWidget(
                appContext,
                appWidgetManager,
                appWidgetId,
                singleLessonWidgetData
            )
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