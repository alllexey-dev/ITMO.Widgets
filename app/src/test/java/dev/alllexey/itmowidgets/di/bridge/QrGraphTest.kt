package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCache
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrToolkit
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrWidgetImages
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
class QrGraphTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the screen and the widget share the one repository Koin builds`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<QrCodeRepositoryImpl>(), koin.get<QrCodeRepository>())
        assertSame(koin.get<QrCodeRepository>(), koin.get<QrToolkit>().repository)
    }

    @Test
    fun `the widget bitmaps and their cache are Koin singles, the cache a session cleaner`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<QrWidgetImages>(), koin.get<QrWidgetImages>())
        assertSame(koin.get<QrBitmapCacheImpl>(), koin.get<QrBitmapCache>())
        assertEquals(1, koin.getAll<SessionDataCleaner>().count { it === koin.get<QrBitmapCacheImpl>() })
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
