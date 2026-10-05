package dev.alllexey.itmowidgets.feature.qr.reference

import android.view.View
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's XML pass screen (`QrCodeFragment`) in every state, opened from home in the debug host with its QR fixture,
 * under the names of LH-3's `QrPassScreen` previews; LH-3's first record overwrites them and its diff is the parity
 * diff. Synthetic pass only.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class QrReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-qr")

    @After
    fun resetFixture() {
        SettingsNavigationTestActivity.qrCode = PASS
        SettingsNavigationTestActivity.qrRefreshResult = AppResult.Success(Unit)
        SettingsNavigationTestActivity.qrDelayMs = 0
    }

    @Test
    fun loading() = pass("QrPassScreenLoadingPreview", code = null, delayMs = PENDING_MS) {
        it.findViewById<View>(R.id.loading).isShown
    }

    @Test
    fun content() = pass("QrPassScreenContentPreview") {
        it.findViewById<View>(R.id.qr_image).isShown && it.findViewById<View>(R.id.refresh_button).isEnabled
    }

    @Test
    fun refreshing() = pass("QrPassScreenRefreshingPreview", delayMs = PENDING_MS) {
        it.findViewById<View>(R.id.qr_image).isShown && !it.findViewById<View>(R.id.refresh_button).isEnabled
    }

    @Test
    fun empty() = pass("QrPassScreenEmptyPreview", code = null) {
        it.findViewById<View>(R.id.state_container).isShown
    }

    @Test
    fun error() = pass("QrPassScreenErrorPreview", code = null, refresh = AppResult.Failure(AppError.Network)) {
        it.findViewById<View>(R.id.state_container).isShown
    }

    private fun pass(
        preview: String,
        code: QrCodeSnapshot? = PASS,
        refresh: AppResult<Unit> = AppResult.Success(Unit),
        delayMs: Long = 0,
        shows: (View) -> Boolean,
    ) {
        SettingsNavigationTestActivity.qrCode = code
        SettingsNavigationTestActivity.qrRefreshResult = refresh
        SettingsNavigationTestActivity.qrDelayMs = delayMs
        references.host(
            preview,
            SettingsNavigationTestActivity::class.java,
            appearance = { SettingsNavigationTestActivity.appearance = it },
            ready = { activity ->
                val screen = qrScreen(activity)
                if (screen == null) open(activity)
                screen != null && screen.isAtRest() && shows(screen)
            },
            view = { qrScreen(it)!! },
        )
    }

    /** Through the host's navigator, as the home FAB does. */
    private fun open(activity: SettingsNavigationTestActivity) {
        if (activity.navigation.overlayHost == null) activity.openScreen(AppScreen.QR_PASS)
    }

    private fun qrScreen(activity: SettingsNavigationTestActivity): View? =
        activity.navigation.overlayHost?.childFragmentManager?.primaryNavigationFragment?.view

    /** The overlay's enter transition has ended: nothing on the way up is faded, moved or scaled. */
    private fun View.isAtRest(): Boolean {
        var view: View? = this
        while (view != null) {
            val moved = view.translationX != 0f || view.translationY != 0f || view.scaleX != 1f || view.scaleY != 1f
            if (view.alpha != 1f || moved) return false
            view = view.parent as? View
        }
        return true
    }

    private companion object {
        val PASS = QrCodeSnapshot("ITMO-TEST", 3_600_000)

        /** Longer than the capture's settle timeout, so the request stays pending. */
        const val PENDING_MS = 60_000L
    }
}
