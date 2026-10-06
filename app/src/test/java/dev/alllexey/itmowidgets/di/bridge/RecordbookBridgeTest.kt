package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.di.barsEngineQualifier
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookModule
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import io.ktor.client.engine.HttpClientEngine
import org.junit.Assert.assertEquals
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

        assertSame(hilt.markTrackingRepository(), koin.get<MarkTrackingRepository>())
        assertSame(hilt.sheetScoresRepository(), koin.get<SheetScoresRepository>())
        assertSame(hilt.subjectBindingStore(), koin.get<SubjectBindingStore>())
        assertSame(hilt.barsEngine(), koin.get<HttpClientEngine>(barsEngineQualifier))
        assertSame(hilt.barsLogin(), koin.get<BarsLogin>())
        // Unscoped in Hilt and stateless: the same implementations, Koin keeps its first instance.
        assertEquals(hilt.barsSilentLogin()::class, koin.get<BarsSilentLogin>()::class)
        assertEquals(hilt.itmoIdCookies()::class, koin.get<ItmoIdCookies>()::class)
        assertEquals(hilt.barsSessionListener()::class, koin.get<BarsSessionListener>()::class)
        assertSame(koin.get<BarsSilentLogin>(), koin.get<BarsSilentLogin>())
    }

    @Test
    fun `the recordbook module passes the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(recordbookModule))
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
