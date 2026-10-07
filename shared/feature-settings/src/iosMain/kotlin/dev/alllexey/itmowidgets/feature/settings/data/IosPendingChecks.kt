package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// The background checks the settings pages are built over, for the iOS graph until each check's own IO card binds the
// real one; that card deletes the class here and its line in `settingsIosModule`.

/**
 * The schedule change check's switch until IO-14 runs the check (IO-09b loads the schedule data): the switch is
 * stored where the real check reads it, so it keeps the user's choice; no check runs yet.
 */
class StoredScheduleChangeTracking(private val preferences: ScheduleCheckPreferences) : ScheduleChangeTracking {

    override fun observeEnabled(): Flow<Boolean> = preferences.observeScheduleChangesEnabled()

    override suspend fun setEnabled(enabled: Boolean) = preferences.setScheduleChangesEnabled(enabled)

    override suspend fun syncWork() = Unit

    override fun stopWork() = Unit

    override fun checkNow() = Unit
}

/** Mark tracking until IO-09d3; its settings page is hidden by `PlatformCapabilities.marks` until then. */
object UnavailableMarkTracking : MarkTracking {

    override suspend fun setMyItmoEnabled(enabled: Boolean) = Unit

    override suspend fun setBarsEnabled(enabled: Boolean) = Unit

    override suspend fun setSheetsEnabled(enabled: Boolean) = Unit

    override suspend fun syncWork() = Unit

    override fun stopWork() = Unit

    override fun checkNow() = Unit
}

/** The calendar sync until IO-15b; its settings rows are hidden by `PlatformCapabilities.calendarExport` until then. */
object UnavailableCalendarSync : CalendarSync {

    override fun observeState(): Flow<CalendarSyncState> = flowOf(CalendarSyncState())

    override suspend fun enable(): CalendarSyncResult = CalendarSyncResult.FAILED

    override suspend fun disable() = Unit

    override suspend fun syncWork() = Unit

    override fun stopWork() = Unit

    override suspend fun requestSync() = Unit
}
