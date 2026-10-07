package dev.alllexey.itmowidgets.feature.schedule.work

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetDataProvider
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetLoadResult
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetProviders
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetRenderer
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ScheduleWidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val dataProvider: ScheduleWidgetDataProvider by inject()
    private val store: ScheduleWidgetSnapshotStore by inject()
    private val diagnostics: AppDiagnostics by inject()

    /** WorkManager can run a worker before `Application.onCreate()` has started Koin. */
    override fun getKoin(): Koin = KoinStarter.ensureStarted(applicationContext)

    override suspend fun doWork(): Result {
        val singleIds = ScheduleWidgetProviders.singleLessonIds(applicationContext)
        val listIds = ScheduleWidgetProviders.dayScheduleIds(applicationContext)
        if (singleIds.isEmpty() && listIds.isEmpty()) {
            ScheduleWidgetWork.cancelIfUnused(applicationContext)
            return Result.success()
        }

        val generation = store.currentGeneration()
        val rendered = try {
            when (val result = dataProvider.load()) {
                is ScheduleWidgetLoadResult.Available -> RenderedSnapshot(
                    snapshot = result.selection.snapshot,
                    nextUpdateDelay = result.selection.nextUpdateDelay
                )

                is ScheduleWidgetLoadResult.Unavailable -> {
                    diagnostics.warn(TAG, "Schedule refresh failed and nothing is cached; keeping the last snapshot")
                    RenderedSnapshot(
                        snapshot = fallbackSnapshot(
                            singleLessonStyle = result.singleLessonStyle,
                            lessonListStyle = result.lessonListStyle
                        ),
                        nextUpdateDelay = ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            diagnostics.warn(TAG, "Widget update failed; keeping the last snapshot", error)
            RenderedSnapshot(
                snapshot = fallbackSnapshot(),
                nextUpdateDelay = ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY
            )
        }

        currentCoroutineContext().ensureActive()
        if (!store.writeIfCurrent(rendered.snapshot, generation)) return Result.success()
        // Re-check persisted gates/expiry immediately before either widget type renders.
        val snapshot = store.read()
        currentCoroutineContext().ensureActive()
        if (store.currentGeneration() != generation) return Result.success()
        render(singleIds, listIds, snapshot)
        ScheduleWidgetWork.scheduleNext(applicationContext, rendered.nextUpdateDelay)
        return Result.success()
    }

    private suspend fun fallbackSnapshot(
        singleLessonStyle: LessonStyle? = null,
        lessonListStyle: LessonStyle? = null,
    ): ScheduleWidgetSnapshot {
        val previous = try {
            store.read().withoutPendingSport()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        if (previous?.canBeShownWhenRefreshFails() == true) {
            return previous.copy(
                singleLessonStyle = singleLessonStyle ?: previous.singleLessonStyle,
                lessonListStyle = lessonListStyle ?: previous.lessonListStyle
            )
        }
        return ScheduleWidgetSnapshot.error(
            singleLessonStyle = singleLessonStyle ?: LessonStyle.DOT,
            lessonListStyle = lessonListStyle ?: LessonStyle.DOT
        )
    }

    private fun render(
        singleIds: IntArray,
        listIds: IntArray,
        snapshot: ScheduleWidgetSnapshot,
    ) {
        val manager = AppWidgetManager.getInstance(applicationContext)
        singleIds.forEach { appWidgetId ->
            ScheduleWidgetRenderer.renderSingle(
                context = applicationContext,
                appWidgetManager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot
            )
        }
        listIds.forEach { appWidgetId ->
            ScheduleWidgetRenderer.renderList(
                context = applicationContext,
                appWidgetManager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot
            )
        }
    }

    private companion object {
        const val TAG = "ScheduleWidget"
    }

    private data class RenderedSnapshot(
        val snapshot: ScheduleWidgetSnapshot,
        val nextUpdateDelay: Duration,
    )
}
