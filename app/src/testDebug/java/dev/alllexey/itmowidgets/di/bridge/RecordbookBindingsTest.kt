package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.DataStoreSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsClient
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkReader
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarkTrackingRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.SheetScoresRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.SportSessionBindingsEntryPoint
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

/**
 * The real graph's view of the recordbook data Koin constructs: Hilt hands out Koin's instances through
 * `RecordbookBridge`, so there is one `BarsClient` (one session lock) and one BARS session store per process, and
 * Hilt's sign-out set holds each of the feature's six cleaners once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class RecordbookBindingsTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `Hilt and Koin share one BARS client and the recordbook repositories`() {
        val application = bootApplication()
        val hilt = RecordbookBindingsEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(koin.get<BarsClient>(), hilt.barsClient())
        assertSame(hilt.barsClient(), hilt.barsClient())
        assertSame(koin.get<BarsLogin>(), hilt.barsLogin())
        assertSame(koin.get<BarsMarkReader>(), hilt.barsMarkSource())
        assertSame(koin.get<RecordbookRepositoryImpl>(), hilt.recordbookRepository())
        assertSame(koin.get<BarsPreferenceRepositoryImpl>(), hilt.barsPreferenceRepository())
    }

    @Test
    fun `sign-out reaches each of the six recordbook cleaners once`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val cleaners = EntryPointAccessors.fromApplication(application, SportSessionBindingsEntryPoint::class.java)
            .sessionDataCleaners()

        // Koin's three, through SessionCleanersBridge.
        assertEquals(1, cleaners.count { it === koin.get<RecordbookRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<BarsRecordbookRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<BarsPreferenceRepositoryImpl>() })
        // Hilt's three, until KM-11b2 moves them.
        assertEquals(1, cleaners.count { it is DataStoreSubjectBindingStore })
        assertEquals(1, cleaners.count { it is MarkTrackingRepositoryImpl })
        assertEquals(1, cleaners.count { it is SheetScoresRepositoryImpl })
        assertEquals(6, cleaners.count(::isRecordbookCleaner))
    }

    private fun isRecordbookCleaner(cleaner: SessionDataCleaner): Boolean =
        cleaner::class.java.name.startsWith("dev.alllexey.itmowidgets.feature.recordbook.")

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
