package dev.alllexey.itmowidgets.feature.settings

import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.descendants
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.card.MaterialCardView
import com.google.android.material.transition.MaterialSharedAxis
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsFragment
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toSettingsNavigation
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks
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
        // The schedule page again, at font 1.3 as well: both switches arrive together and fit.
        val specs = (Appearances.default + Appearances.all.first { it.fontScale > 1f }).distinct()
        try {
            for (spec in specs) {
                SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    scenario.onActivity {
                        it.openScreen(AppScreen.SETTINGS, Bundle().apply { putString(SettingsPage.ARGUMENT, SettingsPage.SCHEDULE.name) })
                    }
                    settle()
                    scenario.onActivity { activity ->
                        val fragment = activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment as SettingsFragment
                        val root = fragment.requireView() as ViewGroup
                        assertEquals(spec.fontScale, root.resources.configuration.fontScale, 0.001f)
                        assertEquals(View.GONE, root.findViewById<View>(R.id.settings_progress).visibility)
                        val switches = root.findViewById<ViewGroup>(R.id.sections_container).descendants
                            .filter { it.id == R.id.setting_switch && it.isShown }.toList()
                        assertEquals(2, switches.size)
                        val titles = root.descendants.filterIsInstance<TextView>()
                            .filter { it.id == R.id.setting_title && it.isShown }.map { it.text.toString() }.toList()
                        assertEquals(
                            listOf(
                                activity.getString(R.string.settings_schedule_changes_title),
                                activity.getString(R.string.settings_schedule_sport_auto_sign_title)
                            ),
                            titles
                        )
                        val cards = root.findViewById<ViewGroup>(R.id.sections_container).children
                            .filterIsInstance<MaterialCardView>().toList()
                        assertEquals(2, cards.size)
                        val gap = root.resources.getDimensionPixelSize(R.dimen.design_spacing_group)
                        assertTrue("Untitled sections keep the group gap", cards[1].top - cards[0].bottom >= gap)
                        assertTrue(activity.offlineLoadingFrames.isEmpty())
                        ViewChecks.assertTextFits(root)
                        ViewChecks.assertTouchTargets(root.findViewById(R.id.sections_container), requireWidth = false)
                    }
                    Screenshots.capture("settings-screenshots", "settings-schedule-${spec.name}") { settle() }
                }
            }
            // The recordbook page before BARS answered (one switch) and after (two), with the footer under them.
            for (spec in specs) for (bars in listOf(null, true)) {
                SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
                SettingsNavigationTestActivity.MemoryMarkTracking.bars.value = bars
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    scenario.onActivity {
                        it.openScreen(AppScreen.SETTINGS, Bundle().apply { putString(SettingsPage.ARGUMENT, SettingsPage.RECORDBOOK.name) })
                    }
                    settle()
                    scenario.onActivity { activity -> assertRecordbookPage(activity, spec.fontScale, barsShown = bars != null) }
                    val state = if (bars == null) "bars-hidden" else "bars-shown"
                    Screenshots.capture("settings-screenshots", "settings-recordbook-${spec.name}-$state") { settle() }
                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
            SettingsNavigationTestActivity.MemoryMarkTracking.bars.value = null
        }
    }

    private fun assertRecordbookPage(activity: SettingsNavigationTestActivity, fontScale: Float, barsShown: Boolean) {
        val fragment = activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment as SettingsFragment
        val root = fragment.requireView() as ViewGroup
        assertEquals(fontScale, root.resources.configuration.fontScale, 0.001f)
        assertEquals(View.GONE, root.findViewById<View>(R.id.settings_progress).visibility)
        val sections = root.findViewById<ViewGroup>(R.id.sections_container)
        val titles = root.descendants.filterIsInstance<TextView>()
            .filter { it.id == R.id.setting_title && it.isShown }.map { it.text.toString() }.toList()
        assertEquals(
            listOfNotNull(
                activity.getString(R.string.settings_marks_myitmo_title),
                activity.getString(R.string.settings_marks_bars_title).takeIf { barsShown }
            ),
            titles
        )
        assertEquals(titles.size, sections.descendants.count { it.id == R.id.setting_switch && it.isShown })
        val footer = sections.descendants.filterIsInstance<TextView>().single { it.id == R.id.setting_section_footer }
        assertTrue(footer.isShown)
        val footerRes = if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) R.string.settings_marks_footer
        else R.string.settings_marks_notifications_off
        assertEquals(activity.getString(footerRes), footer.text.toString())
        assertTrue(activity.offlineLoadingFrames.isEmpty())
        ViewChecks.assertTextFits(root)
        ViewChecks.assertTouchTargets(sections, requireWidth = false)
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
