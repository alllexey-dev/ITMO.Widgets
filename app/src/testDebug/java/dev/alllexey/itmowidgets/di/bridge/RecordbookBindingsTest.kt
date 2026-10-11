package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.auth.data.SessionDataCleaners
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.DataStoreSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.BarsMarksActivation
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarkTrackingRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.SheetScoresRepositoryImpl
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
 * The real graph's view of the recordbook data, all of which Koin constructs: the one BARS client reports to Koin's
 * `BarsMarksActivation`, and sign-out's cleaner list holds each of the feature's six cleaners once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class RecordbookBindingsTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the BARS answer reaches Koin's activation`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<BarsMarksActivation>(), koin.get<BarsSessionListener>())
    }

    @Test
    fun `sign-out reaches each of the six recordbook cleaners once`() {
        bootApplication()
        val koin = GlobalContext.get()
        val cleaners = koin.get<SessionDataCleaners>().current()

        assertEquals(1, cleaners.count { it === koin.get<RecordbookRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<BarsRecordbookRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<BarsPreferenceRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<DataStoreSubjectBindingStore>() })
        assertEquals(1, cleaners.count { it === koin.get<MarkTrackingRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<SheetScoresRepositoryImpl>() })
        assertEquals(6, cleaners.count(::isRecordbookCleaner))
    }

    private fun isRecordbookCleaner(cleaner: SessionDataCleaner): Boolean =
        cleaner::class.java.name.startsWith("dev.alllexey.itmowidgets.feature.recordbook.")

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
