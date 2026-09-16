package dev.alllexey.itmowidgets.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticsCrashHandler
import dev.alllexey.itmowidgets.core.diagnostics.FileAppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.FcmWork
import javax.inject.Inject

@HiltAndroidApp
class ItmoWidgetsApplication : Application() {

    @Inject lateinit var diagnostics: FileAppDiagnostics

    override fun onCreate() {
        super.onCreate()
        DiagnosticsCrashHandler.install(diagnostics)
        diagnostics.importPendingCrashes()
        AppNotificationChannels.create(this)
        FcmWork.syncToken(this)
    }
}
