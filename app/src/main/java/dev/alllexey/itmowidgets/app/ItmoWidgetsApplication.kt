package dev.alllexey.itmowidgets.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticsCrashHandler
import dev.alllexey.itmowidgets.core.diagnostics.FileAppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.create
import dev.alllexey.itmowidgets.core.notification.FcmWork
import dev.alllexey.itmowidgets.core.settings.ThemeMode
import dev.alllexey.itmowidgets.core.storage.AppearancePreferences
import dev.alllexey.itmowidgets.core.ui.AppLocale
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.designsystem.theme.AppColorSource
import dev.alllexey.itmowidgets.di.KoinStarter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
        val appearance = koin.get<AppearancePreferences>()
        // Read before the first activity draws, so Compose screens open in the chosen colours.
        applicationScope.launch { AppColorSource.follow(appearance.observeTheme()) }
        // Light or dark for every activity, its Views and system bars; the widgets keep the launcher's. Read at once:
        // a mode set after an activity started would recreate it.
        AppCompatDelegate.setDefaultNightMode(runBlocking { appearance.observeTheme().first() }.mode.nightMode)
        applicationScope.launch(Dispatchers.Main) {
            appearance.observeTheme().map { it.mode }.collect { AppCompatDelegate.setDefaultNightMode(it.nightMode) }
        }
    }
}

private val ThemeMode.nightMode: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }
