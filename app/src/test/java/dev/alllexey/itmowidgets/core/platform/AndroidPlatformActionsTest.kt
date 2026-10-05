package dev.alllexey.itmowidgets.core.platform

import android.app.Application
import android.content.Intent
import android.provider.Settings
import dev.alllexey.itmowidgets.core.location.MapDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The intents behind each action, the same the View screens start, and false where nothing handles them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidPlatformActionsTest {

    private val application = RuntimeEnvironment.getApplication()
    private val actions = AndroidPlatformActions(application)

    @Test
    fun `share opens the sharesheet for plain text`() {
        assertTrue(actions.shareText("Title", "https://example.org/x"))

        val chooser = started()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("https://example.org/x", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Title", send.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun `an https link opens in the browser and outside an activity in a new task`() {
        assertTrue(actions.openLink("https://example.org/page"))

        val view = started()
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("https://example.org/page", view.dataString)
        assertTrue(view.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(view.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `a t_me link opens in Telegram first`() {
        assertTrue(actions.openLink("https://t.me/itmo_widgets"))

        assertEquals("tg", started().data?.scheme)
    }

    @Test
    fun `a link that is not https opens nothing`() {
        assertFalse(actions.openLink("http://example.org"))
        assertFalse(actions.openLink("javascript:alert(1)"))

        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `a map destination opens as a geo uri`() {
        val destination = MapDestination("Kronverksky 49", "Kronverksky pr. 49", 59.95, 30.31)

        assertTrue(actions.openMap(destination))

        assertEquals(destination.geoUri(), started().dataString)
    }

    @Test
    fun `settings open the app page and its notification page`() {
        assertTrue(actions.openAppSettings())
        val details = started()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, details.action)
        assertEquals("package:${application.packageName}", details.dataString)

        assertTrue(actions.openNotificationSettings())
        val notifications = started()
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifications.action)
        assertEquals(application.packageName, notifications.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun `an action without a handler answers false`() {
        shadowOf(application).checkActivities(true)

        assertFalse(actions.openMap(MapDestination("Nowhere", "Nowhere")))
        assertFalse(actions.openNotificationSettings())
    }

    private fun started(): Intent = checkNotNull(shadowOf(application).nextStartedActivity) { "Nothing was started" }
}
