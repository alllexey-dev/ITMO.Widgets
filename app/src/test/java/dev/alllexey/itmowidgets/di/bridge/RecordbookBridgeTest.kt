package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.DefaultMarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksCheck
import dev.alllexey.itmowidgets.feature.recordbook.di.barsEngineQualifier
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookModule
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.work.MarksEntryPoint
import dev.alllexey.itmowidgets.feature.resources.di.resourcesModule
import dev.alllexey.itmowidgets.feature.reviews.di.reviewsModule
import io.ktor.client.engine.HttpClientEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
class RecordbookBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `what the recordbook takes from the app resolves in Koin to what Hilt builds`() {
        val application = bootApplication()
        val hilt = RecordbookBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.barsEngine(), koin.get<HttpClientEngine>(barsEngineQualifier))
        assertSame(hilt.barsLogin(), koin.get<BarsLogin>())
        // Unscoped in Hilt and stateless: the same implementations, Koin keeps its first instance.
        assertEquals(hilt.barsSilentLogin()::class, koin.get<BarsSilentLogin>()::class)
        assertEquals(hilt.itmoIdCookies()::class, koin.get<ItmoIdCookies>()::class)
        assertEquals(hilt.marksScheduler()::class, koin.get<MarksScheduler>()::class)
        assertEquals(hilt.marksNotifier()::class, koin.get<MarksNotifier>()::class)
        assertSame(koin.get<BarsSilentLogin>(), koin.get<BarsSilentLogin>())
    }

    @Test
    fun `Hilt takes the mark tracking switches and the marks check from Koin`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val tracking = koin.get<DefaultMarkTracking>()

        assertSame(tracking, koin.get<MarkTracking>())
        assertSame(tracking, RecordbookBridge.markTracking(application))
        assertSame(tracking, RecordbookBridge.marksBackgroundCheck(application))
        // Stateless and unscoped, as Hilt built it: the worker gets a new check from Koin on every run.
        val check = EntryPointAccessors.fromApplication(application, MarksEntryPoint::class.java).marksCheck()
        assertEquals(MarksCheck::class, check::class)
        assertNotSame(check, koin.get<MarksCheck>())
    }

    @Test
    fun `the recordbook module passes the graph check against the release bridges`() {
        // `DemoMode` is defined in the account data module, the schedule gateways in the schedule data module.
        // The subject links and teacher levels are `resourcesModule`'s and `reviewsModule`'s since KM-11f.
        KoinGraphCheck.assertValid(
            KoinModules.bridges,
            listOf(resourcesModule, reviewsModule, recordbookModule) + scheduleDataGraph,
        )
    }

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin and Hilt are up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
