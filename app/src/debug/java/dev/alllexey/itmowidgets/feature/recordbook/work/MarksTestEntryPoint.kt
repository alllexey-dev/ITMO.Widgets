package dev.alllexey.itmowidgets.feature.recordbook.work

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler

/**
 * The app's own scheduling and notification objects of the mark check for instrumented tests; debug builds only.
 * Getter names are unique because every entry point is implemented by the same component.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MarksTestEntryPoint {
    fun marksTracking(): MarkTracking
    fun marksScheduler(): MarksScheduler
    fun marksNotifier(): MarksNotifier
}
