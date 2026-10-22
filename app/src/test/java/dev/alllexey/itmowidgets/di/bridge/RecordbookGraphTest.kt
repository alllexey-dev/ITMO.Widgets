package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.WebViewItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.work.AndroidMarksNotifier
import org.junit.Assert.assertTrue
import org.koin.core.qualifier.named
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.core.session.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.DefaultMarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksCheck
import dev.alllexey.itmowidgets.feature.recordbook.di.barsEngineQualifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import io.ktor.client.engine.HttpClientEngine
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class RecordbookGraphTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `what the recordbook takes from the app is one Koin single each`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertNotSame(koin.get<HttpClientEngine>(), koin.get<HttpClientEngine>(barsEngineQualifier))
        assertSame(koin.get<BarsLogin>(), koin.get<BarsLogin>())
        assertTrue(koin.get<BarsSilentLogin>() is BarsWebSilentLogin)
        assertTrue(koin.get<ItmoIdCookies>() is WebViewItmoIdCookies)
        assertTrue(koin.get<MarksNotifier>() is AndroidMarksNotifier)
        assertSame(koin.get<MarksScheduler>(), koin.get<MarksScheduler>())
        assertSame(koin.get<BarsSilentLogin>(), koin.get<BarsSilentLogin>())
    }

    @Test
    fun `the mark tracking switches are one single, also the marks background check`() {
        bootApplication()
        val koin = GlobalContext.get()
        val tracking = koin.get<DefaultMarkTracking>()

        assertSame(tracking, koin.get<MarkTracking>())
        assertSame(tracking, koin.get<BackgroundCheck>(named("marks")))
        // Stateless and unscoped: the worker gets a new check from Koin on every run.
        assertNotSame(koin.get<MarksCheck>(), koin.get<MarksCheck>())
    }

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin is up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
