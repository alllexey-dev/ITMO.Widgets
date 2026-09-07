package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetEntryPoint
import kotlinx.coroutines.runBlocking

class ScheduleWidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return Factory(applicationContext)
    }

    private class Factory(
        private val context: Context
    ) : RemoteViewsFactory {

        private val rowRenderer = ScheduleListRowRenderer(context)
        private val store = ScheduleWidgetEntryPoint.from(context).scheduleWidgetSnapshotStore()
        private var snapshot = ScheduleWidgetSnapshot.loading()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            snapshot = runBlocking { store.read() }
        }

        override fun getCount(): Int = snapshot.lessonList.size

        override fun getViewAt(position: Int): RemoteViews? {
            val item = snapshot.lessonList.getOrNull(position) ?: return null
            return rowRenderer.render(item, snapshot.lessonListStyle)
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = VIEW_TYPE_COUNT

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = true

        override fun onDestroy() = Unit

        private companion object {
            const val VIEW_TYPE_COUNT = 9
        }
    }
}
