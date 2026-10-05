package dev.alllexey.itmowidgets.core.platform

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import dev.alllexey.itmowidgets.designsystem.host.ItmoComposeHost
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.designsystem.host.NoPlatformActions
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Fragment-hosted Compose screens get the activity's [AndroidPlatformActions]; previews get [NoPlatformActions]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlatformComposeHostTest {

    @After
    fun resetHook() = ItmoComposeHost.install { content -> content() }

    @Test
    fun `the installed host provides the android actions`() {
        PlatformComposeHost.install()
        var actions: PlatformActions? = null

        render { ItmoComposeHost.locals { actions = LocalPlatformActions.current } }

        assertTrue(actions.toString(), actions is AndroidPlatformActions)
    }

    @Test
    fun `without a host a screen gets actions that handle nothing`() {
        var actions: PlatformActions? = null

        render { actions = LocalPlatformActions.current }

        assertSame(NoPlatformActions, actions)
    }

    private fun render(content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.setContentView(ComposeView(activity).apply { setContent(content) })
        shadowOf(Looper.getMainLooper()).idle()
    }
}
