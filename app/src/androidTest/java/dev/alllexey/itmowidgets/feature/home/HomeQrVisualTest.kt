package dev.alllexey.itmowidgets.feature.home

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeViewModel
import dev.alllexey.itmowidgets.feature.qr.ui.QrPassTestTags
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.toSettingsNavigation
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel

/**
 * The home FAB opens the Compose pass in its Fragment host, which keeps the pass across recreation and closes on back.
 * The screen's own look and states are JVM screenshots (`QrScreenshotTest`); here only the host, read through the
 * screen's test tags.
 */
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
                        assertTrue(qrRoot(it) is ComposeView)
                        assertNotNull(node(it, QrPassTestTags.IMAGE))
                        val area = node(it, QrPassTestTags.AREA)!!.size
                        assertEquals(area.width, area.height)
                        assertTrue(area.width <= 300 * it.resources.displayMetrics.density + 1)
                    }
                    capture("qr-$index")
                    scenario.recreate()
                    settle()
                    scenario.onActivity {
                        assertEquals(R.id.qr_pass, it.navigation.overlayHost!!.navController.currentDestination!!.id)
                        assertNotNull(node(it, QrPassTestTags.IMAGE))
                    }
                    for ((name, error) in listOf("empty" to null, "error" to AppError.Network)) {
                        SettingsNavigationTestActivity.qrCode = null
                        SettingsNavigationTestActivity.qrRefreshResult =
                            error?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)
                        scenario.onActivity { click(it, QrPassTestTags.REFRESH) }
                        settle()
                        capture("qr-$name-$index")
                        scenario.onActivity {
                            assertNull(node(it, QrPassTestTags.IMAGE))
                            assertNotNull(node(it, QrPassTestTags.STATE))
                            assertTrue(isEnabled(node(it, QrPassTestTags.REFRESH)!!))
                        }
                    }
                    SettingsNavigationTestActivity.qrDelayMs = 60_000
                    scenario.onActivity { click(it, QrPassTestTags.REFRESH) }
                    settle()
                    capture("qr-loading-$index")
                    scenario.onActivity {
                        assertNotNull(node(it, QrPassTestTags.LOADING))
                        assertFalse(isEnabled(node(it, QrPassTestTags.REFRESH)!!))
                        val fragment = it.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!
                        // The Fragment's own instance, from Koin as the route obtains it.
                        val viewModel = fragment.getViewModel<QrCodeViewModel>()
                        viewModel.stop()
                        SettingsNavigationTestActivity.qrDelayMs = 0
                        SettingsNavigationTestActivity.qrCode = QrCodeSnapshot("RETRY-TEST", 3_600_000)
                        SettingsNavigationTestActivity.qrRefreshResult = AppResult.Success(Unit)
                        viewModel.start()
                    }
                    settle()
                    scenario.onActivity {
                        assertNotNull(node(it, QrPassTestTags.IMAGE))
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
            SettingsNavigationTestActivity.appearance = PreviewAppearance()
            SettingsNavigationTestActivity.qrDelayMs = 0
            SettingsNavigationTestActivity.qrRefreshResult = AppResult.Success(Unit)
        }
    }

    private fun qrRoot(activity: SettingsNavigationTestActivity) =
        activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!.requireView()

    /** The node tagged [tag] in the pass's unmerged semantics tree, or null when the screen does not show it. */
    private fun node(activity: SettingsNavigationTestActivity, tag: String): SemanticsNode? {
        val owner = ((qrRoot(activity) as ViewGroup).getChildAt(0) as ViewRootForTest).semanticsOwner
        return generateSequence(listOf(owner.unmergedRootSemanticsNode)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
    }

    private fun isEnabled(node: SemanticsNode) = SemanticsProperties.Disabled !in node.config

    private fun click(activity: SettingsNavigationTestActivity, tag: String) {
        val onClick = node(activity, tag)!!.config.getOrNull(SemanticsActions.OnClick)
        assertTrue("$tag has no click action", onClick?.action?.invoke() == true)
    }

    private fun settle() = TestUi.settle(650)

    private fun capture(name: String) = Screenshots.capture("home-qr-screenshots", name)
}
