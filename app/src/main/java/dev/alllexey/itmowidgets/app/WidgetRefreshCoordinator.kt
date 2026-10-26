package dev.alllexey.itmowidgets.app

import android.content.Context
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.qr.work.QrWidgetWork
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetWork
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.sport.work.SportWidgetWork

class WidgetRefreshCoordinator(
    private val context: Context,
    private val pickerPreviews: WidgetPickerPreviews,
) : WidgetRefreshRequester, ScheduleWidgetRefreshRequester {

    /** After a widget setting or the theme changed: the placed widgets and the picker's previews. */
    override fun refreshAll() {
        pickerPreviews.publish()
        QrWidgetWork.enqueueUpdate(context, force = true)
        ScheduleWidgetWork.enqueueUpdate(context, force = true)
        SportWidgetWork.enqueueUpdate(context, force = true)
    }

    /** After a sport action: the first fetch may still see the schedule from before it; the sport widget changes too. */
    override fun refreshScheduleWidgets() {
        ScheduleWidgetWork.enqueueUpdate(context, force = true)
        ScheduleWidgetWork.enqueueFollowUp(context)
        SportWidgetWork.enqueueUpdate(context, force = true)
    }
}
