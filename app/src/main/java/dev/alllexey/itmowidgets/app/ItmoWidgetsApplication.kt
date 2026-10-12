package dev.alllexey.itmowidgets.app

import android.app.Application
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticsCrashHandler
import dev.alllexey.itmowidgets.core.diagnostics.FileAppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.create
import dev.alllexey.itmowidgets.core.notification.FcmWork
import dev.alllexey.itmowidgets.core.storage.AppearancePreferences
import dev.alllexey.itmowidgets.core.ui.AppLocale
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.designsystem.theme.AppColorSource
import dev.alllexey.itmowidgets.di.KoinStarter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ItmoWidgetsApplication : Application() {

    /** The start-up sync of the background checks. Instrumented tests join it before they enqueue the same work. */
    lateinit var backgroundChecksSync: Job
        private set

    override fun onCreate() {
        val koin = KoinStarter.ensureStarted(this)
        super.onCreate()
        val diagnostics = koin.get<FileAppDiagnostics>()
        val applicationScope = koin.get<CoroutineScope>()
        AppLocale.apply(this)
        DiagnosticsCrashHandler.install(diagnostics)
        diagnostics.importPendingCrashes()
        AppNotificationChannels.create(this)
        FcmWork.syncToken(this)
        // Enrols the periodic checks after an update or a restore, and drops them when the session is gone.
        backgroundChecksSync = applicationScope.launch {
            koin.getAll<BackgroundCheck>().forEach { it.syncWork() }
        }
        // Read before the first activity draws, so Compose screens open in the chosen colours.
        applicationScope.launch { AppColorSource.follow(koin.get<AppearancePreferences>().observeTheme()) }
    }
}
