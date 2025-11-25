package dev.alllexey.itmowidgets.workers

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.ui.widgets.LessonListWidget
import dev.alllexey.itmowidgets.ui.widgets.SingleLessonWidget
import dev.alllexey.itmowidgets.ui.widgets.data.LessonListWidgetData
import dev.alllexey.itmowidgets.ui.widgets.data.LessonListWidgetEntry
import dev.alllexey.itmowidgets.ui.widgets.data.LessonWidgetDataManager
import dev.alllexey.itmowidgets.ui.widgets.data.SingleLessonWidgetData
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val UPDATE_PERIOD_SECONDS = 7L * 60 // 7 minutes

class LessonWidgetUpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appContainer = (appContext as ItmoWidgetsApp).appContainer
        val storage = appContainer.storage
        val repository = appContainer.scheduleRepository
        val lessonListRepository = appContainer.lessonListRepository
        storage.utility.setLastUpdateTimestamp(System.currentTimeMillis())

        val appWidgetManager = AppWidgetManager.getInstance(appContext)

        val singleWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(appContext, SingleLessonWidget::class.java)
        )
        val listWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(appContext, LessonListWidget::class.java)
        )

        if (singleWidgetIds.isEmpty() && listWidgetIds.isEmpty()) {
            return Result.success()
        }

        try {
            val onlyDataChanged = !storage.utility.getLessonWidgetStyleChanged()

            val dataManager = LessonWidgetDataManager(repository, appContainer)
            val widgetsState = dataManager.getLessonWidgetsState()
            lessonListRepository.setData(widgetsState.lessonListWidgetData)
            updateSingleLessonWidgets(
                appWidgetManager,
                singleWidgetIds,
                widgetsState.singleLessonData
            )

            updateLessonListWidgets(
                appWidgetManager,
                listWidgetIds,
                onlyDataChanged
            )

            storage.utility.setLessonWidgetStyleChanged(false)

            scheduleNextUpdate(appContext, widgetsState.nextUpdateAt.plusSeconds(3))
        } catch (e: Exception) {
            e.printStackTrace()
            appContainer.errorLogRepository.logThrowable(e, javaClass.name)
            lessonListRepository.setData(LessonListWidgetData(listOf(LessonListWidgetEntry.Error)))
            scheduleNextUpdate(appContext, null)
        }

        return Result.success()
    }

    private fun updateSingleLessonWidgets(
        appWidgetManager: AppWidgetManager,
        widgetIds: IntArray,
        data: SingleLessonWidgetData
    ) {
        widgetIds.forEach { appWidgetId ->
            SingleLessonWidget.updateAppWidget(appContext, appWidgetManager, appWidgetId, data)
        }
    }

    private fun updateLessonListWidgets(
        appWidgetManager: AppWidgetManager,
        widgetIds: IntArray,
        onlyDataChanged: Boolean
    ) {
        widgetIds.forEach { appWidgetId ->
            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            val outerLayoutId = providerInfo.initialLayout
            LessonListWidget.updateAppWidget(appContext, appWidgetManager, appWidgetId, outerLayoutId, onlyDataChanged)
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
