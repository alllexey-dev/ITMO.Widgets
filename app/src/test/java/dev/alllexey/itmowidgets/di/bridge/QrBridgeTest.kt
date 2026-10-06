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
import dev.alllexey.itmowidgets.feature.qr.work.QrWidgetEntryPoint
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
    fun `the widget reads the one repository Koin builds`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val widget = QrWidgetEntryPoint.from(application)

        assertSame(koin.get<QrCodeRepositoryImpl>(), koin.get<QrCodeRepository>())
        assertSame(koin.get<QrCodeRepository>(), widget.qrCodeRepository())
        assertSame(widget.qrCodeRepository(), widget.qrCodeRepository())
    }

    @Test
    fun `the widget reads the state store and the colour setting Koin builds`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val widget = QrWidgetEntryPoint.from(application)

        assertSame(koin.get<QrWidgetStateStore>(), widget.qrWidgetStateStore())
        assertSame(koin.get<QrAppearancePreferences>(), widget.qrAppearancePreferences())
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
