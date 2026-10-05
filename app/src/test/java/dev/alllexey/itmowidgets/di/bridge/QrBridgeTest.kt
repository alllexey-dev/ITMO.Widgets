package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrAppearancePreferencesImpl
import dev.alllexey.itmowidgets.feature.qr.di.qrModule
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
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
    fun `the QR pass repository resolves in Koin to the instance Hilt builds`() {
        val application = bootApplication()
        val hilt = QrBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.qrCodeRepository(), koin.get<QrCodeRepository>())
        assertSame(koin.get<QrCodeRepository>(), koin.get<QrCodeRepository>())
    }

    @Test
    fun `the QR colour setting resolves in Koin to the Hilt implementation the widget reads`() {
        bootApplication()

        assertTrue(GlobalContext.get().get<QrAppearancePreferences>() is QrAppearancePreferencesImpl)
    }

    @Test
    fun `the QR module passes the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(qrModule))
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
