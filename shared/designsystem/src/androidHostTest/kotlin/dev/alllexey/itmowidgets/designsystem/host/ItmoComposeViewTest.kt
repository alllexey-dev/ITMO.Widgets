package dev.alllexey.itmowidgets.designsystem.host

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoSpacing
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A Fragment's [itmoComposeView] renders inside the installed host locals and [ItmoTheme], and dies with the view. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItmoComposeViewTest {

    @After
    fun resetHook() = ItmoComposeHost.install { content -> content() }

    @Test
    fun `content sees the host locals and the theme`() {
        ItmoComposeHost.install { content -> CompositionLocalProvider(LocalProbe provides "host", content = content) }
        val fragment = ProbeFragment()
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(fragment.view is ComposeView)
        assertEquals("host", fragment.probe)
        assertEquals(ItmoSpacing.Default, fragment.spacing)
        assertEquals(true, fragment.themed)

        val view = fragment.requireView() as ComposeView
        activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        assertFalse(view.hasComposition)
    }

    class ProbeFragment : Fragment() {
        var probe: String? = null
        var spacing: ItmoSpacing? = null
        var themed: Boolean? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            itmoComposeView {
                probe = LocalProbe.current
                spacing = ItmoTheme.spacing
                // material3's own typography uses FontFamily.SansSerif; ItmoTheme's uses the system default.
                themed = MaterialTheme.typography.headlineLarge.fontFamily == FontFamily.Default
            }
    }

    private companion object {
        val LocalProbe = staticCompositionLocalOf<String?> { null }
    }
}
