package dev.alllexey.itmowidgets.feature.social

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.descendants
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserFriendsVisualTest {
    @Test fun profileFriendsAndEveryListStateInLightDarkAndDynamicThemes() {
        val appearances = listOf(
            SettingsNavigationTestActivity.Appearance(),
            SettingsNavigationTestActivity.Appearance(dark = true),
            SettingsNavigationTestActivity.Appearance(fontScale = 1.3f, colorSeed = 0xff087f5b.toInt()),
            SettingsNavigationTestActivity.Appearance(dark = true, fontScale = 1.3f, colorSeed = 0xff087f5b.toInt())
        )
        try {
            appearances.forEachIndexed { index, appearance ->
                SettingsNavigationTestActivity.appearance = appearance
                SettingsNavigationTestActivity.friendsOpen = true
                SettingsNavigationTestActivity.friendsResult = AppResult.Success(listOf(UserProfile(
                    UserSummary(100003, SettingsNavigationTestActivity.LONG_NAME, null, emptyList(), UserSharing(false, false, true)),
                    RelationshipState.NONE
                )))
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    scenario.onActivity { it.openScreen(AppScreen.USER_PROFILE, bundleOf(UserScreenArgs.ISU to 100002)) }
                    settle()
                    capture("profile-$index")
                    scenario.onActivity {
                        val root = it.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!.requireView()
                        val friends = root.findViewById<View>(R.id.friends_row)
                        assertTrue(friends.isEnabled)
                        assertTrue(friends.height >= 48 * it.resources.displayMetrics.density)
                        friends.performClick()
                    }
                    settle()
                    scenario.onActivity { assertEquals(R.id.user_friends, it.navigation.overlayHost!!.navController.currentDestination!!.id) }
                    capture("friends-$index")
                    scenario.onActivity {
                        val fragment = it.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!
                        val list = fragment.requireView().findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_view)
                        assertEquals(1, list.adapter!!.itemCount)
                        list.descendants.filterIsInstance<TextView>().filter { view -> view.visibility == View.VISIBLE }.forEach { text ->
                            assertTrue("Clipped ${text.id}", text.layout == null || text.layout.height <= text.height - text.totalPaddingTop - text.totalPaddingBottom + 1)
                        }
                        list.getChildAt(0).performClick()
                    }
                    settle()
                    scenario.onActivity {
                        assertEquals(R.id.user_profile, it.navigation.overlayHost!!.navController.currentDestination!!.id)
                        assertEquals(100003, it.navigation.overlayHost!!.navController.currentBackStackEntry!!.arguments!!.getInt(UserScreenArgs.ISU))
                        it.onBackPressedDispatcher.onBackPressed()
                    }
                    settle()
                    for ((name, result) in listOf(
                        "empty" to AppResult.Success(emptyList<UserProfile>()),
                        "denied" to AppResult.Failure(AppError.Forbidden),
                        "error" to AppResult.Failure(AppError.Network),
                        "disabled" to AppResult.Failure(AppError.CustomServicesDisabled)
                    )) {
                        SettingsNavigationTestActivity.friendsResult = result
                        scenario.onActivity {
                            val fragment = it.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!
                            ViewModelProvider(fragment)[UserFriendsViewModel::class.java].load()
                        }
                        settle()
                        capture("friends-$name-$index")
                        scenario.onActivity {
                            val root = it.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!.requireView()
                            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.state_container).visibility)
                            assertEquals(if (name == "denied") View.GONE else View.VISIBLE, root.findViewById<View>(R.id.state_action).visibility)
                        }
                    }
                    SettingsNavigationTestActivity.friendsDelayMs = 60_000
                    scenario.onActivity {
                        val fragment = it.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!
                        ViewModelProvider(fragment)[UserFriendsViewModel::class.java].load()
                    }
                    SystemClock.sleep(150)
                    capture("friends-loading-$index")
                    scenario.onActivity {
                        val root = it.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!.requireView()
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.loading).visibility)
                    }
                    SettingsNavigationTestActivity.friendsDelayMs = 0
                    scenario.recreate()
                    settle()
                    scenario.onActivity { assertEquals(R.id.user_friends, it.navigation.overlayHost!!.navController.currentDestination!!.id) }
                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
            SettingsNavigationTestActivity.friendsDelayMs = 0
            SettingsNavigationTestActivity.friendsResult = AppResult.Success(emptyList())
        }
    }

    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(500)
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "social-visual").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
