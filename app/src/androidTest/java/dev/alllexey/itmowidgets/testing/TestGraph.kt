package dev.alllexey.itmowidgets.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.di.KoinStarter

/** The application's real Koin graph for instrumented tests: `TestGraph.get<SessionRepository>()`. */
object TestGraph {

    inline fun <reified T : Any> get(): T =
        KoinStarter.ensureStarted(ApplicationProvider.getApplicationContext<Context>()).get()
}
