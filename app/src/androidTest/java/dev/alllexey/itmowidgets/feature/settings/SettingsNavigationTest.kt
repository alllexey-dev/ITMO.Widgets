package dev.alllexey.itmowidgets.feature.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.descendants
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.transition.MaterialSharedAxis
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsFragment
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @Test
    fun privacyDialogsUseThreeAudiencesAndPersistEachChoiceIndependently() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.openScreen(AppScreen.SETTINGS, Bundle().apply {
                    putString(SettingsPage.ARGUMENT, SettingsPage.PRIVACY.name)
                })
            }
            settle()
            assertPrivacyValues(scenario, "Друзья", "Друзья")

            onView(withText(R.string.settings_schedule_sharing_title)).perform(click())
            assertAudienceDialog(selectedIndex = 1)
            savePrivacyScreenshot("settings-privacy-dialog-friends")
            onView(withText(R.string.settings_privacy_all)).inRoot(isDialog()).perform(click())
            settle()
            assertPrivacyValues(scenario, "Все", "Друзья")

            onView(withText(R.string.settings_sport_sharing_title)).perform(click())
            assertAudienceDialog(selectedIndex = 1)
            onView(withText(R.string.settings_privacy_nobody)).inRoot(isDialog()).perform(click())
            settle()
            assertPrivacyValues(scenario, "Все", "Никто")
            savePrivacyScreenshot("settings-privacy-dialog-selection-saved")

            onView(withText(R.string.settings_schedule_sharing_title)).perform(click())
            assertAudienceDialog(selectedIndex = 0)
            onView(withText(R.string.common_cancel)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.settings_sport_sharing_title)).perform(click())
            assertAudienceDialog(selectedIndex = 2)
            onView(withText(R.string.common_cancel)).inRoot(isDialog()).perform(click())
            assertPrivacyValues(scenario, "Все", "Никто")
        }
    }

    @Test
    fun categoryRowsRemainTouchableAfterForwardAndBackwardTransitions() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.openScreen(AppScreen.SETTINGS) }
            settle()
            repeat(2) {
                onView(withText(R.string.settings_qr_short_title)).perform(click())
                settle()
                scenario.onActivity { activity ->
                    val fragment = activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment as SettingsFragment
                    assertEquals(
                        SettingsPage.QR_WIDGET.name,
                        fragment.requireArguments().getString(SettingsPage.ARGUMENT)
                    )
                }
                onView(withId(R.id.back_button)).perform(click())
                settle()
            }
        }
    }

    @Test
    fun offlinePagesEnterWithCompleteContentAndNoSpinnerFrames() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            for (page in SettingsPage.entries.filter { it != SettingsPage.PRIVACY }) {
                scenario.onActivity { it.openScreen(AppScreen.SETTINGS, Bundle().apply { putString(SettingsPage.ARGUMENT, page.name) }) }
                settle()
                scenario.onActivity { activity ->
                    val fragment = activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment as SettingsFragment
                    assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.settings_progress).visibility)
                    if (page != SettingsPage.ROOT) {
                        assertEquals(220L, (fragment.enterTransition as MaterialSharedAxis).duration)
                    }
                    assertTrue(fragment.returnTransition is MaterialSharedAxis)
                    if (page == SettingsPage.QR_WIDGET) {
                        assertNotNull(fragment.requireView().findViewById<ImageView>(R.id.qr_code_image).drawable)
                    }
                    if (page == SettingsPage.ROOT) {
                        assertTrue(activity.overlayEnteredReady.isNotEmpty())
                        assertTrue(activity.overlayEnteredReady.all { it })
                    } else {
                        assertTrue("A real forward transition must start for $page", activity.entered.any { it.first == page })
                    }
                    assertTrue("First transition frame must be ready", activity.entered.all { it.second })
                    assertTrue("Local screens must never display a spinner", activity.offlineLoadingFrames.isEmpty())
                }
            }
            repeat(SettingsPage.entries.size - 2) {
                scenario.onActivity { assertTrue(it.navigation.overlayHost!!.navController.popBackStack()) }
                settle()
            }
            scenario.recreate()
            settle()
            scenario.onActivity { assertTrue(it.offlineLoadingFrames.isEmpty()) }
        }
    }

    private fun assertAudienceDialog(selectedIndex: Int) {
        onView(isAssignableFrom(ListView::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            val list = view as ListView
            assertEquals(listOf("Все", "Друзья", "Никто"), (0 until list.adapter.count).map { list.adapter.getItem(it).toString() })
            assertEquals(ListView.CHOICE_MODE_SINGLE, list.choiceMode)
            assertEquals(selectedIndex, list.checkedItemPosition)
        }
    }

    private fun assertPrivacyValues(
        scenario: ActivityScenario<SettingsNavigationTestActivity>,
        schedule: String,
        sport: String
    ) {
        scenario.onActivity { activity ->
            val fragment = activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment as SettingsFragment
            val sections = fragment.requireView().findViewById<ViewGroup>(R.id.sections_container)
            for ((title, expected) in listOf(R.string.settings_schedule_sharing_title to schedule, R.string.settings_sport_sharing_title to sport)) {
                val titleView = sections.descendants.filterIsInstance<TextView>().first {
                    it.id == R.id.setting_title && it.text.toString() == activity.getString(title)
                }
                val row = titleView.parent.parent as View
                assertEquals(expected, row.findViewById<TextView>(R.id.setting_value).text.toString())
                assertTrue(row.isEnabled)
            }
        }
    }

    private fun savePrivacyScreenshot(name: String) = Screenshots.capture("settings-screenshots", name) { settle() }

    private fun settle() = TestUi.settle(650)
}
