package dev.alllexey.itmowidgets.di.bridge

import android.app.Application
import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Starts the app's one Koin graph (ADR 0019) beside Hilt.
 *
 * `ItmoWidgetsApplication.onCreate()` calls [ensureStarted] before `super.onCreate()`, where Hilt injects the
 * Application's fields. Every Koin to Hilt bridge calls it as well: WorkManager's `InitializationProvider` can run a
 * worker, and Android can deliver a widget broadcast, before `Application.onCreate()`.
 */
object KoinStarter {

    private val lock = Any()

    /** Returns the running graph, starting it with [KoinModules.all] on the first call from any thread. */
    fun ensureStarted(context: Context): Koin {
        GlobalContext.getOrNull()?.let { return it }
        synchronized(lock) {
            GlobalContext.getOrNull()?.let { return it }
            val application = context as? Application ?: context.applicationContext as Application
            return startKoin {
                // A duplicate definition across modules fails the start instead of replacing a binding silently.
                allowOverride(false)
                androidContext(application)
                modules(KoinModules.all)
            }.koin
        }
    }
}
