package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.feature.qr.di.qrModule
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore
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
class QrBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `Hilt's readers get the one repository Koin builds`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<QrCodeRepositoryImpl>(), koin.get<QrCodeRepository>())
        assertSame(koin.get<QrCodeRepository>(), QrBridge.qrCodeRepository(application))
        assertSame(QrBridge.qrCodeRepository(application), QrBridge.qrCodeRepository(application))
    }

    @Test
    fun `Hilt's readers get the state store and the colour setting Koin builds`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<QrWidgetStateStore>(), QrBridge.qrWidgetStateStore(application))
        assertSame(koin.get<QrAppearancePreferences>(), QrBridge.qrAppearancePreferences(application))
    }

    @Test
    fun `the widget gets its bitmaps from Hilt through Koin`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        // Unscoped in Hilt and stateless: the same implementation, Koin keeps its first instance.
        assertEquals(QrBridgeEntryPoint.from(application).qrWidgetImages()::class, koin.get<QrWidgetImages>()::class)
        assertSame(koin.get<QrWidgetImages>(), koin.get<QrWidgetImages>())
    }

    @Test
    fun `the QR module passes the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(authDataModule, qrModule))
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
