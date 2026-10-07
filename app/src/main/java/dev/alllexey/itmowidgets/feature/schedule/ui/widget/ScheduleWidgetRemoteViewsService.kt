package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * The day list's adapter before rows went inline: a launcher may still bind this cached adapter intent until the
 * first re-render after an update, so it serves the same rows and ids as [ScheduleListRowRenderer.collectionItems].
 */
class ScheduleWidgetRemoteViewsService : RemoteViewsService(), KoinComponent {

    /** Through the one idempotent starter, as every Android component; see [KoinStarter]. */
    override fun getKoin(): Koin = KoinStarter.ensureStarted(applicationContext)

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return Factory(applicationContext, get())
    }

    private class Factory(
        context: Context,
        private val store: ScheduleWidgetSnapshotStore
    ) : RemoteViewsFactory {

        private val rowRenderer = ScheduleListRowRenderer(context)
        private var rows = rowRenderer.rows(ScheduleWidgetSnapshot.loading())

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            rows = rowRenderer.rows(runBlocking { store.read() })
        }

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews? = rows.getOrNull(position)?.views

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = ScheduleListRowRenderer.VIEW_TYPE_COUNT

        override fun getItemId(position: Int): Long = rows.getOrNull(position)?.id ?: position.toLong()

        override fun hasStableIds(): Boolean = true

        override fun onDestroy() = Unit
    }
}
