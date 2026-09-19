package dev.alllexey.itmowidgets.feature.home

import android.view.View
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeViewModel
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toSettingsNavigation
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeQrVisualTest {
    @Test fun homeFabOpensQrAndAllStatesFitThemesAndRecreation() {
        try {
            Appearances.default.forEachIndexed { index, spec ->
                SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
                SettingsNavigationTestActivity.qrCode = QrCodeSnapshot("ITMO-TEST", 3_600_000)
                SettingsNavigationTestActivity.qrRefreshResult = AppResult.Success(Unit)
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    settle()
                    capture("home-$index")
                    scenario.onActivity {
                        val fab = it.findViewById<View>(R.id.qr_fab)
                        assertTrue(fab.height >= 48 * it.resources.displayMetrics.density)
                        assertEquals(it.getString(R.string.home_open_qr), fab.contentDescription)
                        fab.performClick()
                    }
                    settle()
                    scenario.onActivity {
                        assertEquals(R.id.qr_pass, it.navigation.overlayHost!!.navController.currentDestination!!.id)
                        val root = qrRoot(it)
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.qr_image).visibility)
                        assertNotNull(root.findViewById<ImageView>(R.id.qr_image).drawable)
                        val area = root.findViewById<View>(R.id.qr_area)
                        assertEquals(area.width, area.height)
                    }
                    capture("qr-$index")
                    scenario.recreate()
                    settle()
                    scenario.onActivity { assertEquals(View.VISIBLE, qrRoot(it).findViewById<View>(R.id.qr_image).visibility) }
                    for ((name, error) in listOf("empty" to null, "error" to AppError.Network)) {
                        SettingsNavigationTestActivity.qrCode = null
                        SettingsNavigationTestActivity.qrRefreshResult = error?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)
                        scenario.onActivity { qrRoot(it).findViewById<View>(R.id.refresh_button).performClick() }
                        settle()
                        capture("qr-$name-$index")
                        scenario.onActivity {
                            assertEquals(View.GONE, qrRoot(it).findViewById<View>(R.id.qr_image).visibility)
                            assertEquals(View.VISIBLE, qrRoot(it).findViewById<View>(R.id.state_container).visibility)
                            assertTrue(qrRoot(it).findViewById<View>(R.id.refresh_button).isEnabled)
                        }
                    }
                    SettingsNavigationTestActivity.qrDelayMs = 60_000
                    scenario.onActivity { qrRoot(it).findViewById<View>(R.id.refresh_button).performClick() }
                    settle()
                    capture("qr-loading-$index")
                    scenario.onActivity {
                        assertEquals(View.VISIBLE, qrRoot(it).findViewById<View>(R.id.loading).visibility)
                        assertFalse(qrRoot(it).findViewById<View>(R.id.refresh_button).isEnabled)
                        val fragment = it.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!
                        ViewModelProvider(fragment)[QrCodeViewModel::class.java].stop()
                        SettingsNavigationTestActivity.qrDelayMs = 0
                        SettingsNavigationTestActivity.qrCode = QrCodeSnapshot("RETRY-TEST", 3_600_000)
                        SettingsNavigationTestActivity.qrRefreshResult = AppResult.Success(Unit)
                        ViewModelProvider(fragment)[QrCodeViewModel::class.java].start()
                    }
                    settle()
                    scenario.onActivity {
                        assertEquals(View.VISIBLE, qrRoot(it).findViewById<View>(R.id.qr_image).visibility)
                        it.onBackPressedDispatcher.onBackPressed()
                    }
                    settle()
                    scenario.onActivity {
                        assertNull(it.navigation.overlayHost)
                        assertTrue(it.findViewById<View>(R.id.qr_fab).isShown)
                    }
                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
            SettingsNavigationTestActivity.qrDelayMs = 0
            SettingsNavigationTestActivity.qrRefreshResult = AppResult.Success(Unit)
        }
    }

    private fun qrRoot(activity: SettingsNavigationTestActivity) =
        activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!.requireView()

    private fun settle() = TestUi.settle(650)

    private fun capture(name: String) = Screenshots.capture("home-qr-screenshots", name)
}
