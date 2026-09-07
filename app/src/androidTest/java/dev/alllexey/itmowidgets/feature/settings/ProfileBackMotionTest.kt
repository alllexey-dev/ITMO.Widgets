package dev.alllexey.itmowidgets.feature.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.core.view.descendants
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.me.ui.MeFragment
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ProfileBackMotionTest {
    @Test
    fun recreatedProfileMovesAsOneSurfaceDuringCompletedAndCancelledBackGestures() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.host.navController.navigate(R.id.navigation_me) }
            settle()
            lateinit var originalView: View
            lateinit var originalFragment: MeFragment
            scenario.onActivity {
                originalFragment = profile(it)
                originalView = originalFragment.requireView()
            }
            onView(withId(R.id.settings_row)).perform(click())
            settle()
            scenario.onActivity { assertEquals(null, originalFragment.view) }

            // Exercise both gesture edges, including cancellation and a subsequent successful pop.
            for ((edge, cancel) in listOf(BackEventCompat.EDGE_LEFT to true, BackEventCompat.EDGE_RIGHT to false)) {
                scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackStarted(event(0f, edge)) }
                settle(100)
                for (progress in listOf(0.1f, 0.5f, 0.9f)) {
                    scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackProgressed(event(progress, edge)) }
                    settle(80)
                    scenario.onActivity { activity ->
                        // Non-seekable transitions may defer the incoming view until commit.
                        (originalFragment.view as? ViewGroup)?.let { root ->
                            assertNotSame(originalView, root)
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
                    assertEquals(if (cancel) R.id.settings else R.id.navigation_me, it.host.navController.currentDestination?.id)
                }
            }
            scenario.onActivity {
                val root = profile(it).requireView() as ViewGroup
                assertNotSame(originalView, root)
                assertGroupedProfile(root)
                capture(it, "completed")
            }
            // Re-creation must preserve the same invariant, not only a click-time flag.
            onView(withId(R.id.settings_row)).perform(click())
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
        assertTrue("A recreated profile must remain one transition target", root.isTransitionGroup)
        root.descendants.forEach {
            assertEquals("Profile children must not slide inside their clipping parents", 0f, it.translationX, 0.01f)
            assertEquals(0f, it.translationY, 0.01f)
        }
        assertEquals("Александрова Мария Александровна", root.findViewById<TextView>(R.id.profile_name).text)
        assertTrue(root.findViewById<TextView>(R.id.profile_meta).text.contains("123456"))
    }

    private fun event(progress: Float, edge: Int) = BackEventCompat(
        if (edge == BackEventCompat.EDGE_LEFT) 300f * progress else 1080f - 300f * progress,
        800f, progress, edge
    )

    private fun capture(activity: SettingsNavigationTestActivity, name: String) {
        val root = activity.findViewById<View>(R.id.settings_test_container)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val folder = File(activity.externalCacheDir, "profile-back-screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun settle(milliseconds: Long = 400) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(milliseconds)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
