package dev.alllexey.itmowidgets.feature.schedule.work

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetDataProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleWidgetEntryPoint {

    fun scheduleWidgetDataProvider(): ScheduleWidgetDataProvider

    fun scheduleWidgetSnapshotStore(): ScheduleWidgetSnapshotStore

    companion object {

        fun from(context: Context): ScheduleWidgetEntryPoint {
            return EntryPointAccessors.fromApplication(
                context.applicationContext,
                ScheduleWidgetEntryPoint::class.java
            )
        }
    }
}
