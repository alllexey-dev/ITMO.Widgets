package dev.alllexey.itmowidgets.feature.web.reference

import android.view.View
import android.webkit.WebView
import androidx.core.view.isVisible
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.web.ui.MyItmoWebFragment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Today's My ITMO page under the name of LA-7's `MyItmoWebScreen` preview, in `shared/feature-account/screenshots/`.
 * Only the error state: a WebView does not render under Robolectric, and nothing is loaded.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MyItmoWebReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-account")

    @Test
    fun error() = references.fragment(
        "MyItmoWebScreen_error",
        ready = { fragment ->
            val view = fragment.requireView()
            val browser = view.findViewById<WebView>(R.id.web_view)
            val error = view.findViewById<View>(R.id.state_container)
            // A main frame outside the official origins is the error page the screen shows instead of the browser.
            if (!error.isVisible) shadowOf(browser).webViewClient.onPageStarted(browser, OUTSIDE_URL, null)
            error.isVisible
        },
    ) { MyItmoWebFragment() }

    private companion object {
        const val OUTSIDE_URL = "https://example.com/"
    }
}
