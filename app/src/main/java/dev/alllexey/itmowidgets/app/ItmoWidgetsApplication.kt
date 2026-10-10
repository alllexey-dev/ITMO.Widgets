package dev.alllexey.itmowidgets.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticsCrashHandler
import dev.alllexey.itmowidgets.core.diagnostics.FileAppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.create
import dev.alllexey.itmowidgets.core.coroutines.ApplicationScope
import dev.alllexey.itmowidgets.core.notification.FcmWork
import dev.alllexey.itmowidgets.core.storage.AppearancePreferences
import dev.alllexey.itmowidgets.core.ui.AppLocale
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.designsystem.theme.AppColorSource
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class ItmoWidgetsApplication : Application() {

    @Inject lateinit var diagnostics: FileAppDiagnostics
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
    @Inject lateinit var backgroundChecks: Set<@JvmSuppressWildcards BackgroundCheck>
    @Inject lateinit var appearance: AppearancePreferences

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
            backgroundChecks.forEach { it.syncWork() }
        }
        // Read before the first activity draws, so Compose screens open in the chosen colours.
        applicationScope.launch { AppColorSource.follow(appearance.observeAccentColor()) }
    }
}
