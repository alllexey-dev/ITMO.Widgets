package dev.alllexey.itmowidgets.feature.settings

import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.core.view.descendants
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.me.ui.MeFragment
import dev.alllexey.itmowidgets.feature.me.ui.MeTestTags
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ProfileBackMotionTest {
    @Test
    fun profileStaysMountedAndStationaryDuringCompletedAndCancelledOverlayBackGestures() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.host.navController.navigate(R.id.navigation_me) }
            settle()
            lateinit var originalView: View
            lateinit var originalFragment: MeFragment
            scenario.onActivity {
                originalFragment = profile(it)
                originalView = originalFragment.requireView()
            }
            scenario.onActivity { openSettings(it) }
            settle()
            scenario.onActivity { assertSame(originalView, originalFragment.requireView()) }

            // Exercise both gesture edges, including cancellation and a subsequent successful pop.
            for ((edge, cancel) in listOf(BackEventCompat.EDGE_LEFT to true, BackEventCompat.EDGE_RIGHT to false)) {
                scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackStarted(event(0f, edge)) }
                settle(100)
                for (progress in listOf(0.1f, 0.5f, 0.9f)) {
                    scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackProgressed(event(progress, edge)) }
                    settle(80)
                    scenario.onActivity { activity ->
                        // The root view stays mounted even during an interactive overlay gesture.
                        (originalFragment.view as? ViewGroup)?.let { root ->
                            assertSame(originalView, root)
                            assertGroupedProfile(root)
                            capture(activity, "edge-$edge-$progress")
                        }
                    }
                }
                scenario.onActivity {
                    if (cancel) it.onBackPressedDispatcher.dispatchOnBackCancelled()
                    else it.onBackPressedDispatcher.onBackPressed()
                }
                settle()
                scenario.onActivity {
                    assertEquals(R.id.navigation_me, it.host.navController.currentDestination?.id)
                    assertEquals(cancel, it.navigation.overlayHost != null)
                }
            }
            scenario.onActivity {
                val root = profile(it).requireView() as ViewGroup
                assertSame(originalView, root)
                assertGroupedProfile(root)
                capture(it, "completed")
            }
            // Re-creation must preserve the same invariant, not only a click-time flag.
            scenario.onActivity { openSettings(it) }
            settle()
            scenario.recreate()
            settle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            settle()
            scenario.onActivity {
                assertGroupedProfile(profile(it).requireView() as ViewGroup)
                assertTrue(it.groupedProfileFrames.isNotEmpty())
                assertTrue("No profile frame may animate clipped children separately", it.groupedProfileFrames.all { grouped -> grouped })
            }
        }
    }

    private fun profile(activity: SettingsNavigationTestActivity): MeFragment =
        activity.host.childFragmentManager.fragments.filterIsInstance<MeFragment>().single()

    private fun assertGroupedProfile(root: ViewGroup) {
        assertTrue("The profile must remain a stationary surface", root.isTransitionGroup)
        root.descendants.forEach {
            assertEquals("Profile children must not slide inside their clipping parents", 0f, it.translationX, 0.01f)
            assertEquals(0f, it.translationY, 0.01f)
        }
        assertEquals("Александрова Мария Александровна", text(root, MeTestTags.PROFILE_NAME))
        assertTrue(text(root, MeTestTags.PROFILE_META).contains("123456"))
    }

    /** Clicks the Me settings row through the tab's Compose semantics. Main thread only. */
    private fun openSettings(activity: SettingsNavigationTestActivity) {
        val onClick = node(profile(activity).requireView(), MeTestTags.SETTINGS_ROW).config[SemanticsActions.OnClick]
        assertTrue("The settings row has no click action", onClick.action?.invoke() == true)
    }

    private fun text(root: View, tag: String): String =
        node(root, tag).config[SemanticsProperties.Text].joinToString()

    /** The node tagged [tag] in the unmerged semantics of the Fragment's `ComposeView` [root]. */
    private fun node(root: View, tag: String): SemanticsNode {
        val owner = ((root as ViewGroup).getChildAt(0) as ViewRootForTest).semanticsOwner
        return generateSequence(listOf(owner.unmergedRootSemanticsNode)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten().first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
    }

    private fun event(progress: Float, edge: Int) = BackEventCompat(
        if (edge == BackEventCompat.EDGE_LEFT) 300f * progress else 1080f - 300f * progress,
        800f, progress, edge
    )

    /** Called from the main thread inside `onActivity`. */
    private fun capture(activity: SettingsNavigationTestActivity, name: String) =
        Screenshots.draw("profile-back-screenshots", name, activity.findViewById(R.id.main))

    private fun settle(milliseconds: Long = 400) = TestUi.settle(milliseconds)
}
