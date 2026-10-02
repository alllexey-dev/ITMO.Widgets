package dev.alllexey.itmowidgets.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticsCrashHandler
import dev.alllexey.itmowidgets.core.diagnostics.FileAppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.coroutines.ApplicationScope
import dev.alllexey.itmowidgets.core.notification.FcmWork
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class ItmoWidgetsApplication : Application() {

    @Inject lateinit var diagnostics: FileAppDiagnostics
    @Inject @field:ApplicationScope lateinit var applicationScope: CoroutineScope
    @Inject lateinit var scheduleChangeTracking: ScheduleChangeTracking
    @Inject lateinit var markTracking: MarkTracking
    @Inject lateinit var calendarSync: CalendarSync

    override fun onCreate() {
        super.onCreate()
        DiagnosticsCrashHandler.install(diagnostics)
        diagnostics.importPendingCrashes()
        AppNotificationChannels.create(this)
        FcmWork.syncToken(this)
        // Enrols the periodic checks after an update or a restore, and drops them when the session is gone.
        applicationScope.launch {
            scheduleChangeTracking.syncWork()
            markTracking.syncWork()
            calendarSync.syncWork()
        }
    }
}
