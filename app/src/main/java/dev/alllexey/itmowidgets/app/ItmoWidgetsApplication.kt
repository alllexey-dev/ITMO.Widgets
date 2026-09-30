package dev.alllexey.itmowidgets.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticsCrashHandler
import dev.alllexey.itmowidgets.core.diagnostics.FileAppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.coroutines.ApplicationScope
import dev.alllexey.itmowidgets.core.notification.FcmWork
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class ItmoWidgetsApplication : Application() {

    @Inject lateinit var diagnostics: FileAppDiagnostics
    @Inject @field:ApplicationScope lateinit var applicationScope: CoroutineScope
    @Inject lateinit var scheduleChangeTracking: ScheduleChangeTracking

    override fun onCreate() {
        super.onCreate()
        DiagnosticsCrashHandler.install(diagnostics)
        diagnostics.importPendingCrashes()
        AppNotificationChannels.create(this)
        FcmWork.syncToken(this)
        // Enrols the periodic check after an update or a restore, and drops it when the session is gone.
        applicationScope.launch { scheduleChangeTracking.syncWork() }
    }
}
