package dev.alllexey.itmowidgets.feature.settings

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
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
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.card.MaterialCardView
import com.google.android.material.transition.MaterialSharedAxis
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import androidx.lifecycle.ViewModelProvider
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsFragment
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toSettingsNavigation
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks
import org.hamcrest.Matchers.allOf
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
        // The schedule page again, at font 1.3 as well: all three switches arrive together and fit.
        val specs = (Appearances.default + Appearances.all.first { it.fontScale > 1f }).distinct()
        // The background work row has its own test; here Android lets the app work, so the pages show only switches.
        SettingsNavigationTestActivity.MemoryBackgroundWork.unrestricted = true
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
                        assertEquals(3, switches.size)
                        val titles = root.descendants.filterIsInstance<TextView>()
                            .filter { it.id == R.id.setting_title && it.isShown }.map { it.text.toString() }.toList()
                        assertEquals(
                            listOf(
                                activity.getString(R.string.settings_schedule_changes_title),
                                activity.getString(R.string.settings_schedule_sport_auto_sign_title),
                                activity.getString(R.string.settings_calendar_sync_title),
                                activity.getString(R.string.settings_ics_export_title)
                            ),
                            titles
                        )
                        val cards = root.findViewById<ViewGroup>(R.id.sections_container).children
                            .filterIsInstance<MaterialCardView>().toList()
                        assertEquals(3, cards.size)
                        val gap = root.resources.getDimensionPixelSize(R.dimen.design_spacing_group)
                        assertTrue("Untitled sections keep the group gap", cards[1].top - cards[0].bottom >= gap)
                        assertTrue("The calendar group keeps the gap under the footer", cards[2].top - cards[1].bottom >= gap)
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
            SettingsNavigationTestActivity.MemoryBackgroundWork.unrestricted = false
        }
    }

    @Test
    fun backgroundWorkRowAndHintFit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // A switch turned on without the permission would open the system prompt over the hint.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val specs = (Appearances.default + Appearances.all.first { it.fontScale > 1f }).distinct()
        try {
            for (spec in specs) {
                SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
                for (page in listOf(SettingsPage.SCHEDULE, SettingsPage.RECORDBOOK)) {
                    SettingsNavigationTestActivity.MemoryBackgroundWork.unrestricted = false
                    // The recordbook page with all three mark switches: My ITMO, BARS and the sheets.
                    SettingsNavigationTestActivity.MemoryMarkTracking.bars.value = true.takeIf { page == SettingsPage.RECORDBOOK }
                    ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                        openPage(scenario, page)
                        var switchTop = 0
                        scenario.onActivity { activity ->
                            val root = settingsRoot(activity)
                            assertEquals(spec.fontScale, root.resources.configuration.fontScale, 0.001f)
                            assertBackgroundWorkRow(activity, root)
                            if (page == SettingsPage.RECORDBOOK) assertRowUnderThreeSwitches(activity, root)
                            switchTop = firstSwitchTop(root)
                        }
                        Screenshots.capture("settings-screenshots", "settings-background-work-${spec.name}-${page.name.lowercase()}") { settle() }

                        // Back from the system page with the restriction lifted: the row leaves, the switches stay put.
                        SettingsNavigationTestActivity.MemoryBackgroundWork.unrestricted = true
                        scenario.moveToState(Lifecycle.State.STARTED)
                        scenario.moveToState(Lifecycle.State.RESUMED)
                        settle()
                        scenario.onActivity { activity ->
                            val root = settingsRoot(activity)
                            assertNull(backgroundWorkRow(activity, root))
                            assertEquals(switchTop, firstSwitchTop(root))
                            ViewChecks.assertTextFits(root)
                        }
                    }
                }

                // Turning the schedule check on offers the hint once; turning it off offers nothing.
                SettingsNavigationTestActivity.MemoryBackgroundWork.unrestricted = false
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    openPage(scenario, SettingsPage.SCHEDULE)
                    onView(withText(R.string.settings_schedule_changes_title)).perform(click())
                    settle()
                    onView(hintMessage()).check(doesNotExist())
                    onView(withText(R.string.settings_schedule_changes_title)).perform(click())
                    settle()
                    assertBackgroundWorkDialog()
                    Screenshots.capture("settings-screenshots", "settings-background-work-dialog-${spec.name}") { settle() }
                    onView(withText(R.string.background_work_later)).inRoot(isDialog()).perform(click())
                    settle()
                    onView(hintMessage()).check(doesNotExist())

                    onView(withText(R.string.settings_schedule_changes_title)).perform(click())
                    settle()
                    onView(withText(R.string.settings_schedule_changes_title)).perform(click())
                    settle()
                    onView(hintMessage()).check(doesNotExist())
                    scenario.onActivity { activity -> assertNotNull(backgroundWorkRow(activity, settingsRoot(activity))) }
                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
            SettingsNavigationTestActivity.MemoryBackgroundWork.unrestricted = false
            SettingsNavigationTestActivity.MemoryMarkTracking.bars.value = null
        }
    }

    @Test
    fun calendarRowsFitInEveryStateAndOpenTheirDialogs() {
        val sync = SettingsNavigationTestActivity.calendarSync
        try {
            for (spec in Appearances.default) {
                SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
                val states = listOf(
                    "off" to CalendarSyncState(),
                    "on" to CalendarSyncState(enabled = true),
                    "no-permission" to CalendarSyncState(problem = CalendarSyncProblem.NO_PERMISSION)
                )
                for ((name, state) in states) {
                    sync.state.value = state
                    ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                        openPage(scenario, SettingsPage.SCHEDULE)
                        scenario.onActivity { activity ->
                            val root = settingsRoot(activity)
                            val syncTitle = activity.getString(R.string.settings_calendar_sync_title)
                            val export = activity.getString(R.string.settings_ics_export_title)
                            assertEquals(listOf(syncTitle, export), visibleTitles(root).dropWhile { it != syncTitle })
                            val description = settingRow(root, syncTitle)!!.findViewById<TextView>(R.id.setting_description)
                            assertEquals(
                                activity.getString(
                                    if (state.problem == null) R.string.settings_calendar_sync_description
                                    else R.string.settings_calendar_sync_no_permission
                                ),
                                description.text.toString()
                            )
                            assertEquals(state.enabled, calendarSwitch(activity).isChecked)
                            val exportRow = settingRow(root, export)!!
                            assertEquals(
                                activity.getString(R.string.settings_ics_export_description),
                                exportRow.findViewById<TextView>(R.id.setting_description).text.toString()
                            )
                            assertTrue(exportRow.isClickable && exportRow.isEnabled)
                            val footers = root.descendants.filterIsInstance<TextView>()
                                .filter { it.id == R.id.setting_section_footer && it.isShown }.map { it.text.toString() }.toList()
                            assertEquals(activity.getString(R.string.settings_calendar_sync_footer), footers.last())
                            assertTrue(activity.offlineLoadingFrames.isEmpty())
                            ViewChecks.assertTextFits(root)
                            ViewChecks.assertTouchTargets(root.findViewById(R.id.sections_container), requireWidth = false)
                        }
                        Screenshots.capture("calendar-export-screenshots", "settings-calendar-$name-${spec.name}") { settle() }
                    }
                }

                // Turning on with the permission goes straight to the app's calendar; no picker, no note on turning off.
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).forEach {
                    instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, it)
                }
                sync.state.value = CalendarSyncState()
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    openPage(scenario, SettingsPage.SCHEDULE)
                    onView(withText(R.string.settings_calendar_sync_title)).perform(click())
                    settle()
                    assertTrue(sync.state.value.enabled)
                    scenario.onActivity { activity -> assertTrue(calendarSwitch(activity).isChecked) }
                    onView(withText(R.string.settings_calendar_sync_title)).perform(click())
                    settle()
                    assertFalse(sync.state.value.enabled)

                    onView(withText(R.string.settings_ics_export_title)).perform(click())
                    settle()
                    for (range in listOf(R.string.ics_range_week, R.string.ics_range_two_weeks, R.string.ics_range_semester, R.string.ics_range_custom)) {
                        onView(withText(range)).inRoot(isDialog()).check(matches(isDisplayed()))
                    }
                    Screenshots.capture("calendar-export-screenshots", "ics-ranges-${spec.name}") { settle() }
                    onView(withText(R.string.common_cancel)).inRoot(isDialog()).perform(click())
                    settle()
                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
            sync.state.value = CalendarSyncState()
        }
    }

    @Test
    fun qrTileRowFitsAndLeavesOnceAdded() {
        val specs = (Appearances.default + Appearances.all.first { it.fontScale > 1f }).distinct()
        try {
            for (spec in specs) {
                SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
                SettingsNavigationTestActivity.MemoryQuickSettingsTile.canRequest = true
                SettingsNavigationTestActivity.qrTileAdded.value = false
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    openPage(scenario, SettingsPage.QR_WIDGET)
                    var previewTop = 0
                    var tileRowTop = 0
                    scenario.onActivity { activity ->
                        val root = settingsRoot(activity)
                        assertEquals(spec.fontScale, root.resources.configuration.fontScale, 0.001f)
                        assertEquals(View.GONE, root.findViewById<View>(R.id.settings_progress).visibility)
                        assertEquals(
                            listOf(
                                activity.getString(R.string.settings_qr_tile_title),
                                activity.getString(R.string.settings_qr_dynamic_colors_title)
                            ),
                            visibleTitles(root).take(2)
                        )
                        val row = checkNotNull(settingRow(root, activity.getString(R.string.settings_qr_tile_title)))
                        assertEquals(
                            activity.getString(R.string.settings_qr_tile_description),
                            row.findViewById<TextView>(R.id.setting_description).text.toString()
                        )
                        assertTrue(row.isClickable && row.isEnabled)
                        assertTrue(row.height >= 48 * row.resources.displayMetrics.density - 1)
                        assertTrue(screenTop(row) + row.height <= firstSwitchTop(root))
                        val preview = root.findViewById<ViewGroup>(R.id.widget_preview_container)
                        assertTrue(preview.isShown && preview.childCount > 0)
                        previewTop = screenTop(preview)
                        tileRowTop = IntArray(2).also { row.getLocationInWindow(it) }[1]
                        ViewChecks.assertTextFits(root)
                        ViewChecks.assertTouchTargets(root.findViewById(R.id.sections_container), requireWidth = false)
                    }
                    Screenshots.capture("settings-screenshots", "settings-qr-tile-${spec.name}") { settle() }

                    scenario.onActivity { settingsViewModel(it).onQrTileResult(QrTileAddResult.FAILED) }
                    settle()
                    onView(withText(R.string.settings_qr_tile_failed)).check(matches(isDisplayed()))

                    scenario.onActivity { settingsViewModel(it).onQrTileResult(QrTileAddResult.ADDED) }
                    settle()
                    assertTrue(SettingsNavigationTestActivity.qrTileAdded.value)
                    scenario.onActivity { activity ->
                        val root = settingsRoot(activity)
                        assertNull(settingRow(root, activity.getString(R.string.settings_qr_tile_title)))
                        assertEquals(activity.getString(R.string.settings_qr_dynamic_colors_title), visibleTitles(root).first())
                        // The switches take the row's place; the preview above them does not move.
                        assertEquals(tileRowTop, firstSwitchTop(root))
                        assertEquals(previewTop, screenTop(root.findViewById(R.id.widget_preview_container)))
                        ViewChecks.assertTextFits(root)
                    }
                    Screenshots.capture("settings-screenshots", "settings-qr-tile-${spec.name}-added") { settle() }
                }

                // Below Android 13 there is nothing to ask for.
                SettingsNavigationTestActivity.MemoryQuickSettingsTile.canRequest = false
                SettingsNavigationTestActivity.qrTileAdded.value = false
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    openPage(scenario, SettingsPage.QR_WIDGET)
                    scenario.onActivity { activity ->
                        val root = settingsRoot(activity)
                        assertNull(settingRow(root, activity.getString(R.string.settings_qr_tile_title)))
                        assertEquals(activity.getString(R.string.settings_qr_dynamic_colors_title), visibleTitles(root).first())
                    }
                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
            SettingsNavigationTestActivity.MemoryQuickSettingsTile.canRequest = false
            SettingsNavigationTestActivity.qrTileAdded.value = false
        }
    }

    private fun visibleTitles(root: ViewGroup): List<String> =
        root.findViewById<ViewGroup>(R.id.sections_container).descendants.filterIsInstance<TextView>()
            .filter { it.id == R.id.setting_title && it.isShown }.map { it.text.toString() }.toList()

    private fun settingRow(root: ViewGroup, title: String): View? =
        root.descendants.filterIsInstance<TextView>()
            .firstOrNull { it.id == R.id.setting_title && it.isShown && it.text.toString() == title }
            ?.let { it.parent.parent as View }

    private fun settingsViewModel(activity: SettingsNavigationTestActivity): SettingsViewModel {
        val fragment = activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment as SettingsFragment
        return ViewModelProvider(fragment)[SettingsViewModel::class.java]
    }

    /** My ITMO, BARS and the sheets in this order, each 48 dp, the background work row and the footer below them. */
    private fun assertRowUnderThreeSwitches(activity: SettingsNavigationTestActivity, root: ViewGroup) {
        val sections = root.findViewById<ViewGroup>(R.id.sections_container)
        val titles = root.descendants.filterIsInstance<TextView>()
            .filter { it.id == R.id.setting_title && it.isShown }.map { it.text.toString() }.toList()
        assertEquals(
            listOf(
                activity.getString(R.string.settings_marks_myitmo_title),
                activity.getString(R.string.settings_marks_bars_title),
                activity.getString(R.string.settings_marks_sheets_title),
                activity.getString(R.string.settings_background_work_title)
            ),
            titles
        )
        val switches = sections.descendants.filter { it.id == R.id.setting_switch && it.isShown }.toList()
        assertEquals(3, switches.size)
        val minimum = 48 * root.resources.displayMetrics.density - 1
        switches.forEach { assertTrue((it.parent as View).height >= minimum) }
        val row = checkNotNull(backgroundWorkRow(activity, root))
        val lastSwitch = switches.last()
        assertTrue(screenTop(row) >= screenTop(lastSwitch) + lastSwitch.height)
        val footer = sections.descendants.filterIsInstance<TextView>().single { it.id == R.id.setting_section_footer }
        assertTrue(footer.isShown && screenTop(footer) >= screenTop(row) + row.height)
    }

    private fun screenTop(view: View): Int = IntArray(2).also { view.getLocationOnScreen(it) }[1]

    private fun openPage(scenario: ActivityScenario<SettingsNavigationTestActivity>, page: SettingsPage) {
        scenario.onActivity { it.openScreen(AppScreen.SETTINGS, Bundle().apply { putString(SettingsPage.ARGUMENT, page.name) }) }
        settle()
    }

    private fun calendarSwitch(activity: SettingsNavigationTestActivity): android.widget.CompoundButton =
        settingRow(settingsRoot(activity), activity.getString(R.string.settings_calendar_sync_title))!!
            .findViewById(R.id.setting_switch)

    private fun settingsRoot(activity: SettingsNavigationTestActivity): ViewGroup {
        val fragment = activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment as SettingsFragment
        return fragment.requireView() as ViewGroup
    }

    private fun backgroundWorkRow(activity: SettingsNavigationTestActivity, root: ViewGroup): View? =
        root.descendants.filterIsInstance<TextView>()
            .firstOrNull { it.id == R.id.setting_title && it.isShown && it.text.toString() == activity.getString(R.string.settings_background_work_title) }
            ?.let { it.parent.parent as View }

    private fun assertBackgroundWorkRow(activity: SettingsNavigationTestActivity, root: ViewGroup) {
        assertEquals(View.GONE, root.findViewById<View>(R.id.settings_progress).visibility)
        val row = checkNotNull(backgroundWorkRow(activity, root)) { "The background work row is missing" }
        assertEquals(activity.getString(R.string.background_work_hint), row.findViewById<TextView>(R.id.setting_description).text.toString())
        val icon = row.findViewById<ImageView>(R.id.setting_chevron)
        assertTrue(icon.isShown)
        val size = icon.width
        assertArrayEquals(alphaMask(activity.getDrawable(R.drawable.ic_open_in_new)!!, size), alphaMask(icon.drawable, size))
        // The whole row is the button.
        assertTrue(row.isClickable && row.isEnabled)
        assertTrue(row.height >= 48 * row.resources.displayMetrics.density - 1)
        assertTrue(activity.offlineLoadingFrames.isEmpty())
        ViewChecks.assertTextFits(root)
        ViewChecks.assertTouchTargets(root.findViewById(R.id.sections_container), requireWidth = false)
    }

    private fun firstSwitchTop(root: ViewGroup): Int {
        val switch = root.findViewById<ViewGroup>(R.id.sections_container).descendants.first { it.id == R.id.setting_switch && it.isShown }
        return IntArray(2).also { (switch.parent as View).getLocationInWindow(it) }[1]
    }

    /** Tint aside, the icon's shape: the alpha of every pixel at [size]. */
    private fun alphaMask(drawable: Drawable, size: Int): IntArray {
        val copy = checkNotNull(drawable.constantState).newDrawable().mutate()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        copy.setBounds(0, 0, size, size)
        copy.draw(Canvas(bitmap))
        val pixels = IntArray(size * size).also { bitmap.getPixels(it, 0, size, 0, 0, size, size) }
        bitmap.recycle()
        return IntArray(pixels.size) { pixels[it] ushr 24 }
    }

    /** The dialog's message; the row under it carries a shorter hint as its description. */
    private fun hintMessage() = allOf(withId(android.R.id.message), withText(R.string.background_work_dialog_message))

    private fun assertBackgroundWorkDialog() {
        onView(withText(R.string.settings_background_work_title)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(hintMessage()).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            ViewChecks.assertTextFits(view.rootView)
        }
        for (button in listOf(R.string.background_work_allow, R.string.background_work_later)) {
            onView(withText(button)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                assertTrue(view.isShown && view.height >= 48 * view.resources.displayMetrics.density - 1)
            }
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
                activity.getString(R.string.settings_marks_bars_title).takeIf { barsShown },
                activity.getString(R.string.settings_marks_sheets_title)
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
