package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.CardsWidgetPage
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.CardsWidgetPageStore
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.currentPages
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetProviders.NONE
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetWork
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The cards widget, new in 2.3; the class name is a stable identifier of placed widgets (ADR 0016). Its arrows turn
 * one placed widget to another day ([ACTION_PAGE]); the page returns to the first after
 * [CardsWidgetPage.RESET_AFTER] and at midnight.
 */
class CardsWidgetProvider : AppWidgetProvider(), KoinComponent {

    // Resolved on first use, after onReceive() has started Koin.
    private val store: ScheduleWidgetSnapshotStore by inject()
    private val pages: CardsWidgetPageStore by inject()
    private val palettes: WidgetPaletteSource by inject()
    private val time: AcademicTimeProvider by inject()

    override fun onReceive(context: Context, intent: Intent) {
        // The one idempotent starter, as every Android component (KoinStarter); inject() then reads its graph.
        KoinStarter.ensureStarted(context)
        if (intent.action == ACTION_PAGE) {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val page = intent.getIntExtra(EXTRA_PAGE, 0)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) turn(context, widgetId, page)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        ScheduleWidgetWork.ensurePeriodic(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetIds, scheduleUpdate = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, intArrayOf(appWidgetId), scheduleUpdate = false)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                appWidgetIds.forEach { pages.clear(it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        ScheduleWidgetWork.cancelIfUnused(context)
    }

    /** Shows [page] on [widgetId] at once and plans its return to the first page. */
    private fun turn(context: Context, widgetId: Int, page: Int) {
        val pending = goAsync()
        scope.launch {
            try {
                pages.set(widgetId, CardsWidgetPage(page, time.now()))
                val snapshot = store.read()
                ScheduleWidgetProviders.render(
                    context, snapshot, palettes.current(), time.now(), singleIds = NONE, dayIds = NONE, listIds = NONE,
                    cardsIds = intArrayOf(widgetId), cardsPages = mapOf(widgetId to page),
                )
                // A live widget redraws every minute anyway; otherwise one tick brings the first page back.
                if (!snapshot.isLive) ScheduleWidgetWork.scheduleTick(context, CardsWidgetPage.RESET_AFTER + 1.seconds)
            } finally {
                pending.finish()
            }
        }
    }

    private fun render(context: Context, ids: IntArray, scheduleUpdate: Boolean) {
        val pending = goAsync()
        scope.launch {
            try {
                val now = time.now()
                ScheduleWidgetProviders.render(
                    context, store.read(), palettes.current(), now, singleIds = NONE, dayIds = NONE, listIds = NONE,
                    cardsIds = ids, cardsPages = pages.currentPages(ids, now, time.timeZone),
                )
                // WorkManager toggling its own components re-sends APPWIDGET_UPDATE: only the throttled
                // enqueue and the kept periodic job may follow, or the widget refreshes in a loop.
                if (scheduleUpdate) {
                    ScheduleWidgetWork.ensurePeriodic(context)
                    ScheduleWidgetWork.enqueueUpdate(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_PAGE = "dev.alllexey.itmowidgets.action.CARDS_WIDGET_PAGE"
        private const val EXTRA_PAGE = "page"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Turns [widgetId] to [page]; each widget and page has its own data URI, so the intents never merge. */
        fun pageIntent(context: Context, widgetId: Int, page: Int): PendingIntent {
            val intent = Intent(context, CardsWidgetProvider::class.java)
                .setAction(ACTION_PAGE)
                .setData(Uri.parse("itmowidgets-cards://$widgetId/$page"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(EXTRA_PAGE, page)
            return PendingIntent.getBroadcast(context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
