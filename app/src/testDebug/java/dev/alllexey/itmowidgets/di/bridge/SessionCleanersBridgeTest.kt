package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.feature.sport.data.SportSessionBindingsEntryPoint
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * Hilt's `Set<SessionDataCleaner>`, which sign-out iterates, holds Koin's qualified cleaners through
 * `SessionCleanersBridge`. Read through the debug-only identity entry point of the real graph: a test `@EntryPoint`
 * would only join a `@HiltAndroidTest` component.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class SessionCleanersBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the set holds Koin's QR repository once beside Hilt's bitmap cache`() {
        val application = bootApplication()
        val repository = GlobalContext.get().get<QrCodeRepositoryImpl>()
        val cleaners = cleaners(application)

        assertEquals(1, cleaners.count { it === repository })
        assertEquals(1, cleaners.count { it is QrBitmapCacheImpl })
        assertEquals(cleaners.size, cleaners.toSet().size)
    }

    @Test
    fun `sign-out clears the cached pass and the pass bitmaps`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val repository = koin.get<QrCodeRepositoryImpl>()
        koin.get<QrCodeLocalDataSource>().save(PASS_HEX)
        val qrHex = File(application.cacheDir, "qr_hex")
        val bitmap = File(application.cacheDir, "bitmap_cache/noise/pass.png").apply {
            parentFile?.mkdirs()
            writeText("png")
        }
        assertTrue(qrHex.readText().endsWith("|$PASS_HEX"))
        assertEquals(PASS_HEX, runBlocking { repository.currentQrHex(allowExpired = true) })

        runBlocking { cleaners(application).forEach { it.clearSessionData() } }

        assertFalse(qrHex.exists())
        assertFalse(bitmap.exists())
        assertNull(runBlocking { repository.currentQrHex(allowExpired = true) })
    }

    private fun cleaners(context: Context): Set<SessionDataCleaner> =
        EntryPointAccessors.fromApplication(context, SportSessionBindingsEntryPoint::class.java).sessionDataCleaners()

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin and Hilt are up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }

    private companion object {
        /** Synthetic, not a real pass. */
        const val PASS_HEX = "001122aabb"
    }
}
