package dev.alllexey.itmowidgets.feature.onboarding

import android.accessibilityservice.AccessibilityService
import android.appwidget.AppWidgetManager
import android.view.View
import android.view.ViewGroup
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
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind
import dev.alllexey.itmowidgets.feature.onboarding.ui.OnboardingFragment
import dev.alllexey.itmowidgets.feature.onboarding.ui.OnboardingTestTags
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.toSettingsNavigation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * What only a device shows of the first-run flow, on fixture repositories (no stored preferences, no backend): the
 * real widget previews in the Compose slot and the launcher pin. Everything else is the JVM host tests and goldens
 * of `OnboardingScreen` in `:shared:feature-account`. `:app` has no compose test rule, so nodes are read through the
 * `ComposeView`'s semantics owner.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingVisualTest {

    @After
    fun reset() {
        SettingsNavigationTestActivity.appearance = PreviewAppearance()
        SettingsNavigationTestActivity.onboardingFixture = SettingsNavigationTestActivity.OnboardingFixture()
        SettingsNavigationTestActivity.startDestination = R.id.navigation_home
    }

    @Test
    fun everyWidgetPageDrawsItsRealPreviewAtWidgetHeight() {
        launch { scenario ->
            WidgetKind.entries.forEachIndexed { step, kind ->
                if (step > 0) click(scenario, OnboardingTestTags.NEXT)
                TestUi.eventually(message = "$kind preview") {
                    scenario.onActivity { activity ->
                        val root = activity.onboarding().requireView()
                        val preview = activity.onboarding().previewViews[kind]
                        assertNotNull("$kind preview", preview)
                        preview!!
                        assertTrue("$kind preview is shown", preview.isShown)
                        assertTrue("$kind preview size", preview.width > 0 && preview.height > 0)
                        // The slot is as tall as the widget view, not stretched or clipped by the card.
                        val slot = node(root, OnboardingTestTags.PREVIEW)
                        assertTrue("$kind slot height", abs(slot.size.height - preview.height) <= 1)
                        assertEquals("$kind slot width", slot.size.width, preview.width)
                        // A single-lesson preview is as tall as the widget, never the day list's bounded band.
                        if (kind == WidgetKind.SINGLE_LESSON) {
                            val limit = 200 * root.resources.displayMetrics.density
                            assertTrue("Single lesson height", preview.height < limit)
                        }
                    }
                }
                Screenshots.capture("onboarding-screenshots", "widget-$step") { settle() }
            }
        }
    }

    @Test
    fun thePinButtonAsksTheLauncher() {
        val context = TestUi.instrumentation.targetContext
        assumeTrue(
            "The launcher cannot pin widgets",
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
        )
        launch { scenario ->
            click(scenario, OnboardingTestTags.PIN)
            try {
                // requestPinAppWidget opens the launcher's own confirmation over the flow.
                TestUi.eventually(attempts = 50, delayMillis = 100, message = "The launcher dialog did not open") {
                    scenario.onActivity { assertFalse("The flow keeps the focus", it.hasWindowFocus()) }
                }
            } finally {
                TestUi.instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                TestUi.eventually(attempts = 50, delayMillis = 100, message = "The flow did not come back") {
                    scenario.onActivity { assertTrue(it.hasWindowFocus()) }
                }
            }
        }
    }

    private fun launch(block: (ActivityScenario<SettingsNavigationTestActivity>) -> Unit) {
        SettingsNavigationTestActivity.appearance = Appearances.light.toSettingsNavigation()
        SettingsNavigationTestActivity.startDestination = R.id.onboarding
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            settle()
            block(scenario)
        }
    }

    private fun click(scenario: ActivityScenario<SettingsNavigationTestActivity>, tag: String) {
        scenario.onActivity { activity ->
            val onClick = node(activity.onboarding().requireView(), tag).config.getOrNull(SemanticsActions.OnClick)
            assertTrue("$tag has no click action", onClick?.action?.invoke() == true)
        }
        settle()
    }

    private fun SettingsNavigationTestActivity.onboarding(): OnboardingFragment =
        host.childFragmentManager.fragments.single() as OnboardingFragment

    /** The node tagged [tag] in the unmerged semantics tree of the Fragment's `ComposeView`. */
    private fun node(root: View, tag: String): SemanticsNode {
        val owner = ((root as ViewGroup).getChildAt(0) as ViewRootForTest).semanticsOwner
        return generateSequence(listOf(owner.unmergedRootSemanticsNode)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten().first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
    }

    private fun settle() = TestUi.settle(650)
}
