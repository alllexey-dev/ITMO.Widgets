package dev.alllexey.itmowidgets.feature.settings

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.transition.MaterialSharedAxis
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsRangeKind
import dev.alllexey.itmowidgets.feature.settings.ui.IcsExportBottomSheet
import dev.alllexey.itmowidgets.feature.settings.ui.ics.IcsExportTestTags
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsFragment
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import kotlinx.coroutines.CompletableDeferred
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.toSettingsNavigation
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import org.koin.androidx.viewmodel.ext.android.getViewModel
import androidx.annotation.StringRes
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsTestTags

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

            clickRow(scenario, SettingRowId.SCHEDULE_SHARING)
            settle()
            assertAudienceDialog(selectedIndex = 1)
            savePrivacyScreenshot("settings-privacy-dialog-friends")
            // The open choice survives recreation.
            scenario.recreate()
            settle()
            assertAudienceDialog(selectedIndex = 1)
            clickDialog(R.string.settings_privacy_all)
            settle()
            assertPrivacyValues(scenario, "Все", "Друзья")

            clickRow(scenario, SettingRowId.SPORT_SHARING)
            settle()
            assertAudienceDialog(selectedIndex = 1)
            clickDialog(R.string.settings_privacy_nobody)
            settle()
            assertPrivacyValues(scenario, "Все", "Никто")
            savePrivacyScreenshot("settings-privacy-dialog-selection-saved")

            clickRow(scenario, SettingRowId.SCHEDULE_SHARING)
            settle()
            assertAudienceDialog(selectedIndex = 0)
            clickDialog(R.string.common_cancel)
            settle()
            clickRow(scenario, SettingRowId.SPORT_SHARING)
            settle()
            assertAudienceDialog(selectedIndex = 2)
            clickDialog(R.string.common_cancel)
            settle()
            assertNoDialog()
            assertPrivacyValues(scenario, "Все", "Никто")
        }
    }

    @Test
    fun categoryRowsRemainTouchableAfterForwardAndBackwardTransitions() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.openScreen(AppScreen.SETTINGS) }
            settle()
            repeat(2) {
                clickRow(scenario, SettingRowId.PAGE_QR_WIDGET)
                settle()
                scenario.onActivity { activity ->
                    val fragment = activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment as SettingsFragment
                    assertEquals(
                        SettingsPage.QR_WIDGET.name,
                        fragment.requireArguments().getString(SettingsPage.ARGUMENT)
                    )
                }
                scenario.onActivity { SettingsSemantics.back(settingsRoot(it)) }
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
                    assertFalse(SettingsSemantics.hasProgress(fragment.requireView()))
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
                        assertFalse(SettingsSemantics.hasProgress(root))
                        assertEquals(3, SettingsSemantics.rows(root).count(SettingsSemantics::isSwitch))
                        val titles = SettingsSemantics.titles(root)
                        assertEquals(
                            listOf(
                                activity.getString(R.string.settings_schedule_changes_title),
                                activity.getString(R.string.settings_schedule_sport_auto_sign_title),
                                activity.getString(R.string.settings_calendar_sync_title),
                                activity.getString(R.string.settings_ics_export_title)
                            ),
                            titles
                        )
                        // The three untitled groups: each one row apart from the next by at least the group gap.
                        val (changes, autoSign, calendar) = listOf(
                            SettingRowId.SCHEDULE_CHANGES,
                            SettingRowId.SCHEDULE_SPORT_AUTO_SIGN,
                            SettingRowId.CALENDAR_SYNC
                        ).map { checkNotNull(SettingsSemantics.row(root, it)).boundsInWindow }
                        val gap = root.resources.getDimensionPixelSize(R.dimen.design_spacing_group)
                        assertTrue("Untitled sections keep the group gap", autoSign.top - changes.bottom >= gap)
                        assertTrue("The calendar group keeps the gap under the footer", calendar.top - autoSign.bottom >= gap)
                        assertTrue(activity.offlineLoadingFrames.isEmpty())
                        SettingsSemantics.assertTextFits(root)
                        SettingsSemantics.assertTouchTargets(root)
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
            SettingsNavigationTestActivity.appearance = PreviewAppearance()
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
                        var switchTop = 0f
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
                            assertNull(backgroundWorkRow(root))
                            assertEquals(switchTop, firstSwitchTop(root), 0.5f)
                            SettingsSemantics.assertTextFits(root)
                        }
                    }
                }

                // Turning the schedule check on offers the hint once; turning it off offers nothing.
                SettingsNavigationTestActivity.MemoryBackgroundWork.unrestricted = false
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    openPage(scenario, SettingsPage.SCHEDULE)
                    clickRow(scenario, SettingRowId.SCHEDULE_CHANGES)
                    settle()
                    assertNoDialog()
                    clickRow(scenario, SettingRowId.SCHEDULE_CHANGES)
                    settle()
                    assertBackgroundWorkDialog()
                    Screenshots.capture("settings-screenshots", "settings-background-work-dialog-${spec.name}") { settle() }
                    clickDialog(R.string.background_work_later)
                    settle()
                    assertNoDialog()

                    clickRow(scenario, SettingRowId.SCHEDULE_CHANGES)
                    settle()
                    clickRow(scenario, SettingRowId.SCHEDULE_CHANGES)
                    settle()
                    assertNoDialog()
                    scenario.onActivity { activity -> assertNotNull(backgroundWorkRow(settingsRoot(activity))) }
                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = PreviewAppearance()
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
                            assertEquals(listOf(syncTitle, export), SettingsSemantics.titles(root).dropWhile { it != syncTitle })
                            val description = SettingsSemantics.texts(checkNotNull(SettingsSemantics.row(root, SettingRowId.CALENDAR_SYNC)))[1]
                            assertEquals(
                                activity.getString(
                                    if (state.problem == null) R.string.settings_calendar_sync_description
                                    else R.string.settings_calendar_sync_no_permission
                                ),
                                description
                            )
                            assertEquals(state.enabled, calendarSwitchOn(activity))
                            val exportRow = checkNotNull(SettingsSemantics.row(root, SettingRowId.ICS_EXPORT))
                            assertEquals(
                                activity.getString(R.string.settings_ics_export_description),
                                SettingsSemantics.texts(exportRow)[1]
                            )
                            assertTrue(SettingsSemantics.isActionable(exportRow))
                            assertTrue(activity.offlineLoadingFrames.isEmpty())
                            SettingsSemantics.assertTextFits(root)
                            SettingsSemantics.assertTouchTargets(root)
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
                    clickRow(scenario, SettingRowId.CALENDAR_SYNC)
                    settle()
                    assertTrue(sync.state.value.enabled)
                    scenario.onActivity { activity -> assertTrue(calendarSwitchOn(activity)) }
                    clickRow(scenario, SettingRowId.CALENDAR_SYNC)
                    settle()
                    assertFalse(sync.state.value.enabled)

                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = PreviewAppearance()
            sync.state.value = CalendarSyncState()
        }
    }

    @Test
    fun icsExportSheetShowsEveryStateInOneArea() {
        val export = SettingsNavigationTestActivity.icsExport
        val specs = (Appearances.default + Appearances.all.first { it.dark && it.colorSeed == null && it.fontScale == 1f }).distinct()
        val file = IcsFile("content://dev.alllexey.itmowidgets.files/ics/itmo-schedule.ics", "itmo-schedule.ics", 23)
        try {
            for (spec in specs) {
                SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
                export.result = AppResult.Success(file)
                export.gate = CompletableDeferred()
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    openPage(scenario, SettingsPage.SCHEDULE)
                    clickRow(scenario, SettingRowId.ICS_EXPORT)
                    settle()
                    assertSheetTexts(scenario, "Неделя", "2–8 октября", "2 недели", "2–15 октября", "До конца семестра", "до 31 января", "Свои даты", "Выбрать в календаре")
                    var areaHeight = 0
                    scenario.onActivity { activity ->
                        val root = sheetRoot(activity)
                        areaHeight = sheetAreaHeight(activity)
                        SettingsSemantics.assertTextFits(root)
                        val minimum = 48 * root.resources.displayMetrics.density - 1
                        for (kind in IcsRangeKind.entries) {
                            val row = checkNotNull(SettingsSemantics.node(root, IcsExportTestTags.range(kind))) { "No $kind row" }
                            assertTrue("$kind is ${row.size.height} px high", row.size.height >= minimum)
                        }
                    }
                    Screenshots.capture("calendar-export-screenshots", "ics-sheet-choose-${spec.name}") { settle() }

                    clickSheet(scenario, "Неделя")
                    settle()
                    assertSheetTexts(scenario, string(R.string.ics_preparing))
                    scenario.onActivity { activity -> assertEquals(areaHeight, sheetAreaHeight(activity)) }
                    Screenshots.capture("calendar-export-screenshots", "ics-sheet-preparing-${spec.name}") { settle() }

                    export.gate!!.complete(Unit)
                    settle()
                    assertSheetTexts(scenario, "23 пары, 2–8 октября", string(R.string.ics_send), string(R.string.ics_ready_hint))
                    scenario.onActivity { activity ->
                        assertEquals(areaHeight, sheetAreaHeight(activity))
                        SettingsSemantics.assertTextFits(sheetRoot(activity))
                    }
                    Screenshots.capture("calendar-export-screenshots", "ics-sheet-ready-${spec.name}") { settle() }
                }

                export.gate = null
                export.result = AppResult.Success(null)
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    openPage(scenario, SettingsPage.SCHEDULE)
                    clickRow(scenario, SettingRowId.ICS_EXPORT)
                    settle()
                    clickSheet(scenario, "2 недели")
                    settle()
                    assertSheetTexts(scenario, string(R.string.ics_empty))
                    Screenshots.capture("calendar-export-screenshots", "ics-sheet-empty-${spec.name}") { settle() }
                    clickSheet(scenario, string(R.string.ics_pick_other))
                    settle()
                    assertSheetTexts(scenario, "Неделя")

                    export.result = AppResult.Failure(AppError.Network)
                    clickSheet(scenario, "Неделя")
                    settle()
                    assertSheetTexts(scenario, string(R.string.common_error_network))
                    Screenshots.capture("calendar-export-screenshots", "ics-sheet-error-${spec.name}") { settle() }
                    export.result = AppResult.Success(file)
                    clickSheet(scenario, string(R.string.common_retry))
                    settle()
                    assertSheetTexts(scenario, string(R.string.ics_send))
                }

                // The permission dialog, as asked and after a refusal for good.
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    openPage(scenario, SettingsPage.SCHEDULE)
                    for (locked in listOf(false, true)) {
                        scenario.onActivity { activity -> settingsFragment(activity).showCalendarAccessDialog(locked) }
                        settle()
                        val action = if (locked) R.string.calendar_access_open_settings else R.string.calendar_access_allow
                        assertDialogTexts(R.string.calendar_access_title, R.string.calendar_access_rationale, action)
                        Screenshots.capture("calendar-export-screenshots", "calendar-access-${if (locked) "locked" else "ask"}-${spec.name}") { settle() }
                        clickDialog(R.string.calendar_access_later)
                        settle()
                        assertNoDialog()
                    }
                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = PreviewAppearance()
            export.gate = null
            export.result = AppResult.Success(null)
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
                    var previewTop = 0f
                    var tileRowTop = 0f
                    scenario.onActivity { activity ->
                        val root = settingsRoot(activity)
                        assertEquals(spec.fontScale, root.resources.configuration.fontScale, 0.001f)
                        assertFalse(SettingsSemantics.hasProgress(root))
                        assertEquals(
                            listOf(
                                activity.getString(R.string.settings_qr_tile_title),
                                activity.getString(R.string.settings_qr_dynamic_colors_title)
                            ),
                            SettingsSemantics.titles(root).take(2)
                        )
                        val row = checkNotNull(SettingsSemantics.row(root, SettingRowId.QR_TILE))
                        assertEquals(activity.getString(R.string.settings_qr_tile_description), SettingsSemantics.texts(row)[1])
                        assertTrue(SettingsSemantics.isActionable(row))
                        assertTrue(row.size.height >= 48 * root.resources.displayMetrics.density - 1)
                        assertTrue(row.boundsInWindow.bottom <= firstSwitchTop(root))
                        val preview = checkNotNull(SettingsSemantics.node(root, SettingsTestTags.WIDGET_PREVIEW))
                        // The preview itself is still the View of WidgetPreviewFactory, inside the slot.
                        assertNotNull(root.findViewById<ImageView>(R.id.qr_code_image))
                        previewTop = preview.boundsInWindow.top
                        tileRowTop = row.boundsInWindow.top
                        SettingsSemantics.assertTextFits(root)
                        SettingsSemantics.assertTouchTargets(root)
                    }
                    Screenshots.capture("settings-screenshots", "settings-qr-tile-${spec.name}") { settle() }

                    scenario.onActivity { settingsViewModel(it).onQrTileResult(QrTileAddResult.FAILED) }
                    // A short snackbar: polled at once, not after a settle that a slow emulator can stretch past it.
                    TestUi.eventually { onView(withText(R.string.settings_qr_tile_failed)).check(matches(isDisplayed())) }

                    scenario.onActivity { settingsViewModel(it).onQrTileResult(QrTileAddResult.ADDED) }
                    settle()
                    assertTrue(SettingsNavigationTestActivity.qrTileAdded.value)
                    scenario.onActivity { activity ->
                        val root = settingsRoot(activity)
                        assertNull(SettingsSemantics.row(root, SettingRowId.QR_TILE))
                        assertEquals(activity.getString(R.string.settings_qr_dynamic_colors_title), SettingsSemantics.titles(root).first())
                        // The switches take the row's place; the preview above them does not move.
                        assertEquals(tileRowTop, firstSwitchTop(root), 0.5f)
                        assertEquals(previewTop, checkNotNull(SettingsSemantics.node(root, SettingsTestTags.WIDGET_PREVIEW)).boundsInWindow.top, 0.5f)
                        SettingsSemantics.assertTextFits(root)
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
                        assertNull(SettingsSemantics.row(root, SettingRowId.QR_TILE))
                        assertEquals(activity.getString(R.string.settings_qr_dynamic_colors_title), SettingsSemantics.titles(root).first())
                    }
                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = PreviewAppearance()
            SettingsNavigationTestActivity.MemoryQuickSettingsTile.canRequest = false
            SettingsNavigationTestActivity.qrTileAdded.value = false
        }
    }

    @Test
    fun accountDeletionAndPrivacyRowsOpenTheSite() {
        val specs = (Appearances.default + Appearances.all.first { it.fontScale > 1f }).distinct()
        val links = BrowserLinks()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.addMonitor(links)
        try {
            for (spec in specs) {
                SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    openPage(scenario, SettingsPage.SERVICES)
                    scenario.onActivity { activity ->
                        val root = settingsRoot(activity)
                        assertEquals(spec.fontScale, root.resources.configuration.fontScale, 0.001f)
                        val title = activity.getString(R.string.settings_delete_account_title)
                        assertEquals(title, SettingsSemantics.titles(root).last())
                        val row = checkNotNull(SettingsSemantics.row(root, SettingRowId.DELETE_ACCOUNT))
                        assertEquals(activity.getString(R.string.settings_delete_account_description), SettingsSemantics.texts(row)[1])
                        // The trailing icon is decorative; the row says it leaves the app through the icon it asks for.
                        assertEquals(AppIcon.OPEN_IN_NEW, action(activity, SettingRowId.DELETE_ACCOUNT).trailingIcon)
                        assertTrue(SettingsSemantics.isActionable(row))
                        SettingsSemantics.assertTextFits(root)
                        SettingsSemantics.assertTouchTargets(root)
                    }
                    Screenshots.capture("settings-screenshots", "settings-services-delete-${spec.name}") { settle() }
                    links.opened.clear()
                    clickRow(scenario, SettingRowId.DELETE_ACCOUNT)
                    settle()
                    assertEquals(listOf(BuildConfig.WIDGETS_BASE_URL + "/delete-account"), links.opened.map { it.dataString })

                    openPage(scenario, SettingsPage.MAINTENANCE)
                    scenario.onActivity { activity ->
                        val root = settingsRoot(activity)
                        assertEquals(
                            listOf(activity.getString(R.string.settings_privacy_policy_title), activity.getString(R.string.settings_version_title)),
                            SettingsSemantics.titles(root).takeLast(2)
                        )
                        assertEquals(activity.getString(R.string.app_unofficial_notice), SettingsSemantics.footer(root))
                        // The host builds AppVersion from this resource; the fixture shows its own version.
                        assertEquals(BuildConfig.VERSION_NAME, activity.getString(R.string.app_version))
                        SettingsSemantics.assertTextFits(root)
                    }
                    Screenshots.capture("settings-screenshots", "settings-maintenance-privacy-${spec.name}") { settle() }
                    links.opened.clear()
                    clickRow(scenario, SettingRowId.PRIVACY_POLICY)
                    settle()
                    assertEquals(listOf(BuildConfig.WIDGETS_BASE_URL + "/privacy.html"), links.opened.map { it.dataString })
                }
            }
        } finally {
            instrumentation.removeMonitor(links)
            SettingsNavigationTestActivity.appearance = PreviewAppearance()
        }
    }

    /** Catches every link sent to a browser and opens nothing. */
    private class BrowserLinks : Instrumentation.ActivityMonitor() {
        val opened = CopyOnWriteArrayList<Intent>()

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_VIEW) return null
            opened += intent
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    private fun settingsViewModel(activity: SettingsNavigationTestActivity): SettingsViewModel =
        settingsFragment(activity).getViewModel<SettingsViewModel>()

    /** The row as the page state has it, for what the screen draws without semantics (a decorative icon). */
    private fun action(activity: SettingsNavigationTestActivity, id: SettingRowId): SettingItem.Action =
        settingsViewModel(activity).uiState.value.sections.flatMap { it.items }
            .filterIsInstance<SettingItem.Action>().single { it.id == id }

    /** Clicks a row through its semantics, as TalkBack would; the whole row is the target. */
    private fun clickRow(scenario: ActivityScenario<SettingsNavigationTestActivity>, id: SettingRowId) {
        scenario.onActivity { SettingsSemantics.click(settingsRoot(it), id) }
    }

    /** My ITMO, BARS and the sheets in this order, each 48 dp, the background work row and the footer below them. */
    private fun assertRowUnderThreeSwitches(activity: SettingsNavigationTestActivity, root: ViewGroup) {
        assertEquals(
            listOf(
                activity.getString(R.string.settings_marks_myitmo_title),
                activity.getString(R.string.settings_marks_bars_title),
                activity.getString(R.string.settings_marks_sheets_title),
                activity.getString(R.string.settings_background_work_title)
            ),
            SettingsSemantics.titles(root)
        )
        val switches = SettingsSemantics.rows(root).filter(SettingsSemantics::isSwitch)
        assertEquals(3, switches.size)
        val minimum = 48 * root.resources.displayMetrics.density - 1
        switches.forEach { assertTrue(it.size.height >= minimum) }
        val row = checkNotNull(backgroundWorkRow(root))
        assertTrue(row.boundsInWindow.top >= switches.last().boundsInWindow.bottom)
        val footer = checkNotNull(SettingsSemantics.node(root, SettingsTestTags.FOOTER))
        assertTrue(footer.boundsInWindow.top >= row.boundsInWindow.bottom)
    }

    private fun openPage(scenario: ActivityScenario<SettingsNavigationTestActivity>, page: SettingsPage) {
        scenario.onActivity { it.openScreen(AppScreen.SETTINGS, Bundle().apply { putString(SettingsPage.ARGUMENT, page.name) }) }
        settle()
    }

    private fun settingsFragment(activity: SettingsNavigationTestActivity): SettingsFragment =
        activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment as SettingsFragment

    private fun icsSheet(activity: SettingsNavigationTestActivity): IcsExportBottomSheet =
        settingsFragment(activity).childFragmentManager.findFragmentByTag(IcsExportBottomSheet.TAG) as IcsExportBottomSheet

    /** The sheet's `ComposeView`, read through its semantics as [SettingsSemantics] reads the page. Main thread only. */
    private fun sheetRoot(activity: SettingsNavigationTestActivity): ViewGroup = icsSheet(activity).requireView() as ViewGroup

    /** The one area every state of the sheet takes. */
    private fun sheetAreaHeight(activity: SettingsNavigationTestActivity): Int =
        checkNotNull(SettingsSemantics.node(sheetRoot(activity), IcsExportTestTags.AREA)) { "No sheet area" }.size.height

    /** The sheet shows every one of [texts]; the hidden states say nothing, so only the shown one counts. */
    private fun assertSheetTexts(scenario: ActivityScenario<SettingsNavigationTestActivity>, vararg texts: String) {
        scenario.onActivity { activity ->
            val shown = SettingsSemantics.nodes(sheetRoot(activity)).flatMap { SettingsSemantics.texts(it) }.toSet()
            texts.forEach { text -> assertTrue("No $text in the sheet: $shown", text in shown) }
        }
    }

    /** Clicks the topmost target of the sheet that shows [text]: a range row or a button. */
    private fun clickSheet(scenario: ActivityScenario<SettingsNavigationTestActivity>, text: String) {
        scenario.onActivity { activity ->
            val node = checkNotNull(
                SettingsSemantics.nodes(sheetRoot(activity)).firstOrNull { node ->
                    node.config.getOrNull(SemanticsActions.OnClick) != null && text in SettingsSemantics.texts(node)
                },
            ) { "No $text to click in the sheet" }
            assertTrue("$text did not click", node.config[SemanticsActions.OnClick].action?.invoke() == true)
        }
    }

    private fun calendarSwitchOn(activity: SettingsNavigationTestActivity): Boolean =
        SettingsSemantics.isOn(checkNotNull(SettingsSemantics.row(settingsRoot(activity), SettingRowId.CALENDAR_SYNC)))

    private fun settingsRoot(activity: SettingsNavigationTestActivity): ViewGroup =
        settingsFragment(activity).requireView() as ViewGroup

    private fun backgroundWorkRow(root: ViewGroup): SemanticsNode? = SettingsSemantics.row(root, SettingRowId.BACKGROUND_WORK)

    private fun assertBackgroundWorkRow(activity: SettingsNavigationTestActivity, root: ViewGroup) {
        assertFalse(SettingsSemantics.hasProgress(root))
        val row = checkNotNull(backgroundWorkRow(root)) { "The background work row is missing" }
        assertEquals(activity.getString(R.string.background_work_hint), SettingsSemantics.texts(row)[1])
        assertEquals(AppIcon.OPEN_IN_NEW, action(activity, SettingRowId.BACKGROUND_WORK).trailingIcon)
        // The whole row is the button.
        assertTrue(SettingsSemantics.isActionable(row))
        assertTrue(row.size.height >= 48 * root.resources.displayMetrics.density - 1)
        assertTrue(activity.offlineLoadingFrames.isEmpty())
        SettingsSemantics.assertTextFits(root)
        SettingsSemantics.assertTouchTargets(root)
    }

    private fun firstSwitchTop(root: ViewGroup): Float =
        SettingsSemantics.rows(root).first(SettingsSemantics::isSwitch).boundsInWindow.top

    /** The hint with its message fitting and both buttons at least 48 dp high. */
    private fun assertBackgroundWorkDialog() {
        assertDialogTexts(R.string.settings_background_work_title, R.string.background_work_dialog_message)
        onDialog { root ->
            SettingsSemantics.assertTextFits(root)
            for (button in listOf(R.string.background_work_allow, R.string.background_work_later)) {
                // The layout keeps the 48 dp touch target around the 40 dp button it draws.
                val height = dialogButton(root, button).layoutInfo.height
                assertTrue("${string(button)} is $height px high", height >= 48 * root.resources.displayMetrics.density - 1)
            }
        }
    }

    /** The Compose dialog over the page shows every one of [texts]. */
    private fun assertDialogTexts(@StringRes vararg texts: Int) {
        onDialog { root ->
            val shown = SettingsSemantics.nodes(root).flatMap { SettingsSemantics.texts(it) }.toSet()
            texts.forEach { text -> assertTrue("No ${string(text)} in the dialog", string(text) in shown) }
        }
    }

    private fun clickDialog(@StringRes label: Int) {
        onDialog { root ->
            val node = dialogButton(root, label)
            assertTrue("${string(label)} did not click", node.config[SemanticsActions.OnClick].action?.invoke() == true)
        }
    }

    /** The topmost clickable node of the dialog showing [label]: a button or a choice row. */
    private fun dialogButton(root: ViewGroup, @StringRes label: Int): SemanticsNode =
        checkNotNull(
            SettingsSemantics.nodes(root).firstOrNull { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null && string(label) in SettingsSemantics.texts(node)
            },
        ) { "No ${string(label)} to click in the dialog" }

    private fun assertNoDialog() {
        onFocusedWindow { root -> assertNull("A dialog is still shown", root) }
    }

    /** Runs [block] on the main thread with the Compose dialog over the page; fails when none is shown. */
    private fun onDialog(block: (ViewGroup) -> Unit) {
        onFocusedWindow { root -> block(checkNotNull(root) { "No dialog is shown" }) }
    }

    /**
     * The focused window's `ComposeView` host as [SettingsSemantics] reads it when the window is a dialog, null when
     * it is the activity: the kit's dialogs are windows of their own, outside the page's semantics.
     */
    private fun onFocusedWindow(block: (ViewGroup?) -> Unit) {
        onView(isRoot()).check { root, error ->
            if (error != null) throw error
            val type = (root.layoutParams as? WindowManager.LayoutParams)?.type
            block(if (type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION) null else checkNotNull(composeHost(root)))
        }
    }

    private fun composeHost(view: View): ViewGroup? {
        if (view !is ViewGroup) return null
        if (view.childCount > 0 && view.getChildAt(0) is ViewRootForTest) return view
        return (0 until view.childCount).firstNotNullOfOrNull { composeHost(view.getChildAt(it)) }
    }

    private fun string(@StringRes id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun assertRecordbookPage(activity: SettingsNavigationTestActivity, fontScale: Float, barsShown: Boolean) {
        val root = settingsRoot(activity)
        assertEquals(fontScale, root.resources.configuration.fontScale, 0.001f)
        assertFalse(SettingsSemantics.hasProgress(root))
        val titles = SettingsSemantics.titles(root)
        assertEquals(
            listOfNotNull(
                activity.getString(R.string.settings_marks_myitmo_title),
                activity.getString(R.string.settings_marks_bars_title).takeIf { barsShown },
                activity.getString(R.string.settings_marks_sheets_title)
            ),
            titles
        )
        assertEquals(titles.size, SettingsSemantics.rows(root).count(SettingsSemantics::isSwitch))
        val footerRes = if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) R.string.settings_marks_footer
        else R.string.settings_marks_notifications_off
        assertEquals(activity.getString(footerRes), SettingsSemantics.footer(root))
        assertTrue(activity.offlineLoadingFrames.isEmpty())
        SettingsSemantics.assertTextFits(root)
        SettingsSemantics.assertTouchTargets(root)
    }

    /** The three audiences as radio rows, the current one selected. */
    private fun assertAudienceDialog(selectedIndex: Int) {
        onDialog { root ->
            val options = SettingsSemantics.nodes(root)
                .filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
                .sortedBy { it.boundsInWindow.top }
                .toList()
            assertEquals(listOf("Все", "Друзья", "Никто"), options.map { SettingsSemantics.texts(it).single() })
            assertEquals(selectedIndex, options.indexOfFirst { it.config.getOrNull(SemanticsProperties.Selected) == true })
        }
    }

    private fun assertPrivacyValues(
        scenario: ActivityScenario<SettingsNavigationTestActivity>,
        schedule: String,
        sport: String
    ) {
        scenario.onActivity { activity ->
            val root = settingsRoot(activity)
            for ((id, expected) in listOf(SettingRowId.SCHEDULE_SHARING to schedule, SettingRowId.SPORT_SHARING to sport)) {
                val row = checkNotNull(SettingsSemantics.row(root, id))
                assertEquals(expected, SettingsSemantics.texts(row)[1])
                assertTrue(SettingsSemantics.isActionable(row))
            }
        }
    }

    private fun savePrivacyScreenshot(name: String) = Screenshots.capture("settings-screenshots", name) { settle() }

    private fun settle() = TestUi.settle(650)
}
