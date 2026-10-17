package dev.alllexey.itmowidgets.ios.widgets

import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.schedule.widget.ScheduleTimelineWriter
import dev.alllexey.itmowidgets.feature.sport.widget.SportWidgetWriter
import org.koin.dsl.module

/**
 * The iOS [ScheduleWidgetRefreshRequester], which sport asks after a booking: a booking changes the schedule widgets'
 * day and the sport widget's bookings, so both App Group files are written again.
 */
val widgetRefreshIosModule = module {
    single<ScheduleWidgetRefreshRequester> {
        val schedule = get<ScheduleTimelineWriter>()
        val sport = get<SportWidgetWriter>()
        ScheduleWidgetRefreshRequester {
            schedule.refreshScheduleWidgets()
            sport.refresh()
        }
    }
}
