package dev.alllexey.itmowidgets.feature.qr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.AppOverlayHostFragment
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.feature.qr.ui.QrTileService
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** The QR pass tile in the emulator's real SystemUI, driven by `cmd statusbar`. */
@RunWith(AndroidJUnit4::class)
class QrTileFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val deviceHints = EntryPointAccessors.fromApplication(context, NotificationDebugEntryPoint::class.java).deviceHints()

    @After
    fun removeTheTileAndSignOut() {
        shell("cmd statusbar remove-tile $TILE")
        eventually { assertEquals(false, runBlocking { deviceHints.observeQrTileAdded().first() }) }
        shell("cmd statusbar collapse")
        runCatching { onActivity { it.finish() } }
        TestSession.signOut()
        TestSession.resetOnboarding()
    }

    @Test
    fun tileClickOpensThePassThroughTheSharedRoute() {
        TestSession.seedActiveSession()
        TestSession.completeOnboarding()

        shell("cmd statusbar add-tile $TILE")
        eventually { assertEquals(true, runBlocking { deviceHints.observeQrTileAdded().first() }) }

        shell("cmd statusbar click-tile $TILE")
        eventually {
            onActivity { activity ->
                val root = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                assertEquals(R.id.navigation_home, root.navController.currentDestination?.id)
                val overlay = activity.supportFragmentManager.findFragmentById(R.id.overlay_container) as? AppOverlayHostFragment
                assertNotNull(overlay)
                assertEquals(R.id.qr_pass, overlay!!.navController.currentDestination?.id)
            }
        }
    }

    @Test
    fun passIntentMatchesTheWidgetFlags() {
        val intent = QrTileService.passIntent(context)

        assertEquals(ComponentName(context, MainActivity::class.java), intent.component)
        assertEquals(AppEntryIntents.ACTION_OPEN_QR_PASS, intent.action)
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(flags, intent.flags)
    }

    private fun onActivity(block: (MainActivity) -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                block(activity)
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = 100, delayMillis = 100, message = "The tile did not settle", assertion = assertion)

    private companion object {
        const val TILE = "dev.alllexey.itmowidgets/.feature.qr.ui.QrTileService"
    }
}
