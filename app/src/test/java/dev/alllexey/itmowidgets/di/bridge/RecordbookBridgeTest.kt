package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookModule
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
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
    fun `the recordbook data resolves in Koin to the instances Hilt builds`() {
        val application = bootApplication()
        val hilt = RecordbookBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.recordbookRepository(), koin.get<RecordbookRepository>())
        assertSame(hilt.barsRecordbookRepository(), koin.get<BarsRecordbookRepository>())
        assertSame(hilt.barsPreferenceRepository(), koin.get<BarsPreferenceRepository>())
        assertSame(hilt.barsSessionRepository(), koin.get<BarsSessionRepository>())
        assertSame(hilt.markTrackingRepository(), koin.get<MarkTrackingRepository>())
        assertSame(hilt.sheetScoresRepository(), koin.get<SheetScoresRepository>())
        assertSame(hilt.subjectBindingStore(), koin.get<SubjectBindingStore>())
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
