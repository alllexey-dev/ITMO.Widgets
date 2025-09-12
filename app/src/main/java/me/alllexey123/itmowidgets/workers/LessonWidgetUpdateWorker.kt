package me.alllexey123.itmowidgets.workers

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import api.myitmo.model.Lesson
import me.alllexey123.itmowidgets.providers.ScheduleProvider
import me.alllexey123.itmowidgets.providers.StorageProvider
import me.alllexey123.itmowidgets.utils.ScheduleUtils
import me.alllexey123.itmowidgets.widgets.SingleLessonWidget
import me.alllexey123.itmowidgets.widgets.SingleLessonWidgetData
import me.alllexey123.itmowidgets.widgets.SingleLessonWidgetVariant
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val UPDATE_PERIOD_SECONDS = 7L * 60 // 7 minutes

private const val BEFOREHAND_SCHEDULING_OFFSET = 15L * 60 // 15 minutes

class LessonWidgetUpdateWorker(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val widgetClasses =
            listOf(SingleLessonWidget::class.java, SingleLessonWidgetVariant::class.java)
        val appWidgetIds = mutableListOf<Int>()

        for (clazz in widgetClasses) {
            val widgetProvider = ComponentName(appContext, clazz)
            val ids = appWidgetManager.getAppWidgetIds(widgetProvider)

            appWidgetIds.addAll(ids.toList())
        }

        if (appWidgetIds.isEmpty()) {
            return Result.success()
        }

        val storage = StorageProvider.getStorage(appContext)
        storage.setLastUpdateTimestamp(System.currentTimeMillis())
        var nextUpdateAt: LocalDateTime? = null

        val smartScheduling = storage.getSmartSchedulingState()
        val beforehandScheduling = storage.getBeforehandSchedulingState()

        val singleLessonWidgetData: SingleLessonWidgetData = try {
            val currentDateTime = LocalDateTime.now()

            val currentDate = currentDateTime.toLocalDate()

            val daySchedule = ScheduleProvider.getDaySchedule(appContext, currentDate)
            val lessons = daySchedule.lessons

            if (lessons.isNullOrEmpty()) {
                if (smartScheduling) nextUpdateAt = currentDate.plusDays(1).atStartOfDay()
                SingleLessonWidget.noLessonsWidgetData()
            } else {
                val targetLesson: Lesson?
                if (beforehandScheduling) {
                    val noBeforehand =
                        ScheduleProvider.findCurrentOrNextLesson(lessons, currentDateTime)
                    val withBeforehand = ScheduleProvider.findCurrentOrNextLesson(
                        lessons,
                        currentDateTime.plusSeconds(BEFOREHAND_SCHEDULING_OFFSET)
                    )

                    targetLesson = withBeforehand ?: noBeforehand
                } else {
                    targetLesson =
                        ScheduleProvider.findCurrentOrNextLesson(lessons, currentDateTime)
                }

                val idx = lessons.indexOf(targetLesson)
                val moreLessons = lessons.size - idx - 1
                val till = lessons.last().timeEnd

                if (targetLesson != null) {
                    if (smartScheduling) {
                        val parsedTime = ScheduleUtils.parseTime(currentDate, targetLesson.timeEnd)
                        nextUpdateAt =
                            if (beforehandScheduling && moreLessons > 0) {
                                parsedTime.minusSeconds(BEFOREHAND_SCHEDULING_OFFSET)
                            } else {
                                parsedTime
                            }
                    }

                    SingleLessonWidget.widgetData(targetLesson, moreLessons, till)
                } else {
                    if (smartScheduling) nextUpdateAt = currentDate.plusDays(1).atStartOfDay()
                    SingleLessonWidget.noLeftLessonsWidgetData()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SingleLessonWidget.errorLessonWidgetData()
        }

        for (appWidgetId in appWidgetIds) {

            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            val initialLayoutId = providerInfo.initialLayout
            val realLayoutId = SingleLessonWidget.getLayoutId(initialLayoutId, storage.getDynamicTheme())

            SingleLessonWidget.updateAppWidget(
                appContext,
                appWidgetManager,
                appWidgetId,
                singleLessonWidgetData,
                realLayoutId
            )
        }

        nextUpdateAt = nextUpdateAt?.plusSeconds(70) // to prevent update loops

        scheduleNextUpdate(appContext, nextUpdateAt)

        return Result.success()
    }

    companion object {
        const val WIDGET_UPDATE_WORK_NAME = "me.alllexey123.itmowidgets.LessonWidgetUpdate"

        fun enqueueImmediateUpdate(context: Context) {
            val immediateWorkRequest = OneTimeWorkRequestBuilder<LessonWidgetUpdateWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WIDGET_UPDATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateWorkRequest
            )
        }

        // if "at" is null, then use update period
        fun scheduleNextUpdate(context: Context, at: LocalDateTime?) {
            val durationSeconds: Long
            if (at == null) {
                durationSeconds = UPDATE_PERIOD_SECONDS
            } else {
                val now = LocalDateTime.now()
                durationSeconds = Duration.between(now, at).seconds.coerceAtLeast(0)
            }

            val updateWorkRequest = OneTimeWorkRequestBuilder<LessonWidgetUpdateWorker>()
                .setInitialDelay(durationSeconds, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WIDGET_UPDATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                updateWorkRequest
            )
        }
    }
}