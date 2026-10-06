package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetEntryPoint
import kotlinx.coroutines.runBlocking

/**
 * The day list's adapter before rows went inline: a launcher may still bind this cached adapter intent until the
 * first re-render after an update, so it serves the same rows and ids as [ScheduleListRowRenderer.collectionItems].
 */
class ScheduleWidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return Factory(applicationContext)
    }

    private class Factory(
        private val context: Context
    ) : RemoteViewsFactory {

        private val rowRenderer = ScheduleListRowRenderer(context)
        private val store = ScheduleWidgetEntryPoint.from(context).scheduleWidgetSnapshotStore()
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
