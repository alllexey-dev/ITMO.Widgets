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
import dev.alllexey.itmowidgets.core.ui.AppLocale
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class ItmoWidgetsApplication : Application() {

    @Inject lateinit var diagnostics: FileAppDiagnostics
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
    @Inject lateinit var scheduleChangeTracking: ScheduleChangeTracking
    @Inject lateinit var markTracking: MarkTracking
    @Inject lateinit var calendarSync: CalendarSync

    override fun onCreate() {
        // Koin first: Hilt injects the fields above inside super.onCreate(), and a Koin to Hilt bridge may run there.
        KoinStarter.ensureStarted(this)
        super.onCreate()
        AppLocale.apply(this)
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
