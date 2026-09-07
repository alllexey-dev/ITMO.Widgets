package dev.alllexey.itmowidgets.app

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.qr.work.QrWidgetWork
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetWork
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRefreshCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WidgetRefreshRequester, ScheduleWidgetRefreshRequester {

    override fun refreshAll() {
        QrWidgetWork.enqueueUpdate(context, force = true)
        refreshScheduleWidgets()
    }

    override fun refreshScheduleWidgets() {
        ScheduleWidgetWork.enqueueUpdate(context, force = true)
    }
}
