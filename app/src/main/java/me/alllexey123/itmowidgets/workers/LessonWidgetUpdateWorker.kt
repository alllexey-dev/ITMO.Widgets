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
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.providers.ScheduleProvider
import me.alllexey123.itmowidgets.providers.StorageProvider
import me.alllexey123.itmowidgets.utils.LINE_STYLE
import me.alllexey123.itmowidgets.utils.PreferencesStorage
import me.alllexey123.itmowidgets.utils.ScheduleUtils
import me.alllexey123.itmowidgets.widgets.LessonListWidget
import me.alllexey123.itmowidgets.widgets.SingleLessonData
import me.alllexey123.itmowidgets.widgets.SingleLessonWidget
import me.alllexey123.itmowidgets.widgets.SingleLessonWidgetVariant
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val UPDATE_PERIOD_SECONDS = 7L * 60 // 7 minutes
private const val BEFOREHAND_SCHEDULING_OFFSET = 15L * 60 // 15 minutes

class LessonWidgetUpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(appContext)

        val singleWidgetIds = collectWidgetIds(appWidgetManager, listOf(
            SingleLessonWidget::class.java,
            SingleLessonWidgetVariant::class.java
        ))
        val listWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(appContext, LessonListWidget::class.java)
        )

        if (singleWidgetIds.isEmpty() && listWidgetIds.isEmpty()) {
            return Result.success()
        }

        val storage = StorageProvider.getStorage(appContext).apply {
            setLastUpdateTimestamp(System.currentTimeMillis())
        }

        var nextUpdateAt: LocalDateTime? = null

        loadSingleLessonData(storage).also { (data, nextUpdate) ->
            nextUpdateAt = nextUpdate
            updateSingleLessonWidgets(appWidgetManager, singleWidgetIds, storage, data)
        }

        loadLessonListData().apply {
            updateLessonListWidgets(appWidgetManager, listWidgetIds, storage, this)
        }

        // Small offset to prevent update loops
        nextUpdateAt = nextUpdateAt?.plusSeconds(70)

        scheduleNextUpdate(appContext, nextUpdateAt)
        return Result.success()
    }

    private fun loadSingleLessonData(storage: PreferencesStorage): Pair<SingleLessonData, LocalDateTime?> {
        val smartScheduling = storage.getSmartSchedulingState()
        val beforehandScheduling = storage.getBeforehandSchedulingState()

        return try {
            val now = LocalDateTime.now()
            val currentDate = now.toLocalDate()
            val lessons = ScheduleProvider.getDaySchedule(appContext, currentDate).lessons.orEmpty()

            if (lessons.isEmpty()) {
                val nextUpdate = if (smartScheduling) currentDate.plusDays(1).atStartOfDay() else null
                return SingleLessonWidget.noLessonsWidgetData() to nextUpdate
            }

            val targetLesson = findTargetLesson(lessons, now, beforehandScheduling)
            val moreLessons = targetLesson?.let { lessons.size - lessons.indexOf(it) - 1 }
            val till = lessons.last().timeEnd

            if (targetLesson != null) {
                val nextUpdate = if (smartScheduling) {
                    val parsedTime = ScheduleUtils.parseTime(currentDate, targetLesson.timeEnd)
                    if (beforehandScheduling && moreLessons!! > 0) {
                        parsedTime.minusSeconds(BEFOREHAND_SCHEDULING_OFFSET)
                    } else parsedTime
                } else null

                SingleLessonWidget.widgetData(targetLesson, moreLessons, till) to nextUpdate
            } else {
                val nextUpdate = if (smartScheduling) currentDate.plusDays(1).atStartOfDay() else null
                SingleLessonWidget.noLeftLessonsWidgetData() to nextUpdate
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SingleLessonWidget.errorLessonWidgetData() to null
        }
    }

    private fun updateSingleLessonWidgets(
        appWidgetManager: AppWidgetManager,
        widgetIds: List<Int>,
        storage: PreferencesStorage,
        data: SingleLessonData
    ) {
        val dynamicTheme = storage.getDynamicTheme()
        widgetIds.forEach { appWidgetId ->
            val realLayoutId = if (storage.getSingleLessonWidgetStyle() == LINE_STYLE) {
                SingleLessonWidget.getLayoutId(R.layout.single_lesson_widget, dynamicTheme)
            } else {
                SingleLessonWidget.getLayoutId(R.layout.single_lesson_widget_variant, dynamicTheme)
            }
            SingleLessonWidget.updateAppWidget(appContext, appWidgetManager, appWidgetId, data, realLayoutId)
        }
    }

    private fun findTargetLesson(
        lessons: List<Lesson>,
        now: LocalDateTime,
        beforehandScheduling: Boolean
    ): Lesson? {
        val noBeforehand = ScheduleProvider.findCurrentOrNextLesson(lessons, now)
        return if (beforehandScheduling) {
            val withBeforehand = ScheduleProvider.findCurrentOrNextLesson(
                lessons,
                now.plusSeconds(BEFOREHAND_SCHEDULING_OFFSET)
            )
            withBeforehand ?: noBeforehand
        } else {
            noBeforehand
        }
    }

    private fun loadLessonListData(): List<SingleLessonData> {
        return try {
            val now = LocalDateTime.now()
            val currentDate: LocalDate = now.toLocalDate()
            val lessons = ScheduleProvider.getDaySchedule(appContext, currentDate).lessons.orEmpty()
            lessons.map { SingleLessonWidget.widgetData(it, null, null) }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(SingleLessonWidget.errorLessonWidgetData())
        }
    }

    private fun updateLessonListWidgets(
        appWidgetManager: AppWidgetManager,
        widgetIds: IntArray,
        storage: PreferencesStorage,
        data: List<SingleLessonData>
    ) {
        val dynamicTheme = storage.getDynamicTheme()
        widgetIds.forEach { appWidgetId ->
            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            val realLayoutId = SingleLessonWidget.getLayoutId(
                providerInfo.initialLayout,
                dynamicTheme
            )
            val rowLayoutId = if (storage.getListLessonWidgetStyle() == LINE_STYLE) {
                SingleLessonWidget.getLayoutId(R.layout.single_lesson_widget, dynamicTheme)
            } else {
                SingleLessonWidget.getLayoutId(R.layout.single_lesson_widget_variant, dynamicTheme)
            }
            LessonListWidget.updateAppWidget(appContext, appWidgetManager, appWidgetId, ArrayList(data), realLayoutId, rowLayoutId)
        }
    }

    private fun collectWidgetIds(
        appWidgetManager: AppWidgetManager,
        classes: List<Class<*>>
    ): List<Int> {
        return classes.flatMap { clazz ->
            appWidgetManager.getAppWidgetIds(ComponentName(appContext, clazz)).toList()
        }
    }

    companion object {
        private const val WIDGET_UPDATE_WORK_NAME = "me.alllexey123.itmowidgets.LessonWidgetUpdate"

        fun enqueueImmediateUpdate(context: Context) {
            val request = OneTimeWorkRequestBuilder<LessonWidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WIDGET_UPDATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun scheduleNextUpdate(context: Context, at: LocalDateTime?) {
            val durationSeconds = at?.let {
                Duration.between(LocalDateTime.now(), it).seconds.coerceAtLeast(0)
            } ?: UPDATE_PERIOD_SECONDS

            val request = OneTimeWorkRequestBuilder<LessonWidgetUpdateWorker>()
                .setInitialDelay(durationSeconds, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WIDGET_UPDATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
