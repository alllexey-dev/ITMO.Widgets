package dev.alllexey.itmowidgets.feature.schedule.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetDataProvider
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetLoadResult
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetProviders
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.core.Koin

class ScheduleWidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // WorkManager can run a worker before `Application.onCreate()` has started Koin.
        ScheduleWidgetUpdate(KoinStarter.ensureStarted(applicationContext), applicationContext).run(refresh = true)
        return Result.success()
    }
}

/**
 * One update of the schedule widgets: select (after a fetch with [run]'s `refresh`), store, draw, and plan the next
 * one: the next boundary through the worker and, while a countdown or the `сейчас` line runs, the next minute from
 * the cache.
 */
internal class ScheduleWidgetUpdate(koin: Koin, private val context: Context) {
    // Resolved on first use: without placed widgets the update stops before it reads the graph.
    private val dataProvider: ScheduleWidgetDataProvider by lazy { koin.get() }
    private val store: ScheduleWidgetSnapshotStore by lazy { koin.get() }
    private val diagnostics: AppDiagnostics by lazy { koin.get() }
    private val palettes: WidgetPaletteSource by lazy { koin.get() }
    private val time: AcademicTimeProvider by lazy { koin.get() }

    suspend fun run(refresh: Boolean) {
        val singleIds = ScheduleWidgetProviders.singleLessonIds(context)
        val dayIds = ScheduleWidgetProviders.dayIds(context)
        // The classic list draws no clock: a minute tick (refresh = false) leaves it alone.
        val listIds = if (refresh) ScheduleWidgetProviders.listIds(context) else ScheduleWidgetProviders.NONE
        if (singleIds.isEmpty() && dayIds.isEmpty() && listIds.isEmpty()) {
            ScheduleWidgetWork.cancelIfUnused(context)
            return
        }

        val generation = store.currentGeneration()
        val (selected, nextUpdate) = try {
            when (val result = dataProvider.load(refresh)) {
                is ScheduleWidgetLoadResult.Available -> result.selection.snapshot to result.selection.nextUpdateDelay
                ScheduleWidgetLoadResult.Unavailable -> {
                    diagnostics.warn(TAG, "Schedule refresh failed and nothing is cached; keeping the last snapshot")
                    fallbackSnapshot() to ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            diagnostics.warn(TAG, "Widget update failed; keeping the last snapshot", error)
            fallbackSnapshot() to ScheduleWidgetSelector.PERIODIC_UPDATE_DELAY
        }

        currentCoroutineContext().ensureActive()
        if (!store.writeIfCurrent(selected, generation)) return
        // Re-check persisted gates/expiry immediately before either widget type renders.
        val snapshot = store.read()
        currentCoroutineContext().ensureActive()
        if (store.currentGeneration() != generation) return
        val now = time.now()
        ScheduleWidgetProviders.render(context, snapshot, palettes.current(), now, singleIds, dayIds, listIds)
        if (refresh) ScheduleWidgetWork.scheduleNext(context, nextUpdate)
        if (snapshot.isLive && (singleIds.isNotEmpty() || dayIds.isNotEmpty())) ScheduleWidgetWork.scheduleTick(context, untilNextMinute(now.toEpochMilliseconds()))
        else ScheduleWidgetWork.cancelTick(context)
    }

    private suspend fun fallbackSnapshot(): ScheduleWidgetSnapshot {
        val previous = try {
            store.read().withoutPendingSport()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        return previous?.takeIf { it.canBeShownWhenRefreshFails() } ?: ScheduleWidgetSnapshot.error()
    }

    /** Just past the next whole minute, so the text changes with the clock in the status bar. */
    private fun untilNextMinute(nowMillis: Long): Duration {
        val minute = 1.minutes.inWholeMilliseconds
        return (minute - nowMillis % minute + TICK_LATENESS_MILLIS).milliseconds
    }

    private companion object {
        const val TAG = "ScheduleWidget"
        const val TICK_LATENESS_MILLIS = 500L
    }
}
