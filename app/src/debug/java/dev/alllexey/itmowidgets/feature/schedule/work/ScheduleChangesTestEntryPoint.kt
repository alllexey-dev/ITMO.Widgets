package dev.alllexey.itmowidgets.feature.schedule.work

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler

/**
 * The app's own scheduling and notification objects for instrumented tests; debug builds only.
 * Getter names are unique because every entry point is implemented by the same component.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleChangesTestEntryPoint {
    fun scheduleChangeTracking(): ScheduleChangeTracking
    fun scheduleChangesScheduler(): ScheduleChangesScheduler
    fun scheduleChangeNotifier(): ScheduleChangeNotifier
}
