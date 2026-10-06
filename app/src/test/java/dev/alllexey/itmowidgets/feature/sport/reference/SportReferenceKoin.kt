package dev.alllexey.itmowidgets.feature.sport.reference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import org.junit.rules.ExternalResource
import org.koin.core.context.stopKoin

/**
 * The release Koin graph over this test's `HiltTestApplication`, for the sport pages whose ViewModels come from Koin.
 * A graph another test left behind would forward to that test's Application and Hilt component, so it is stopped
 * first; the bridges read Hilt lazily, after `AppScreenshotRule` created the component (the fixed academic clock
 * included).
 */
class SportReferenceKoin : ExternalResource() {
    override fun before() {
        stopKoin()
        KoinStarter.ensureStarted(ApplicationProvider.getApplicationContext<Context>())
    }

    override fun after() {
        stopKoin()
    }
}
