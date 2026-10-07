package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `app_preferences.preferences_pb` is a stable identifier: released installs keep reading these 43 key names
 * with these defaults. A rename, a duplicate or a moved default is a data loss for every upgrading user. The three
 * `push_registered_*` keys are written only by iOS (IO-13a), whose app process opens the same file name.
 */
class PreferenceKeyParityTest {

    @Test
    fun `the app preferences keep their 43 key names, each declared once`() {
        // The stores live in :app and in the main source sets of the shared modules (KM-06 moved core's there).
        val sharedMain = File("../shared").walk()
            .filter { "${File.separator}build${File.separator}" !in it.path && "Main${File.separator}kotlin" in it.path }
        val declared = (File("src/main/java").walk() + sharedMain)
            .filter { it.isFile && it.extension == "kt" }
            .flatMap(::declaredKeys)
            .sortedWith(compareBy({ it.file }, { it.name }))
            .toList()

        assertEquals(EXPECTED.sortedWith(compareBy({ it.file }, { it.name })), declared)
        assertEquals(43, declared.map { it.name }.toSet().size)
    }

    @Test
    fun `the 28 settings keys read their released defaults from an empty file`() = runTest {
        val stores = PreferenceStores()

        assertFalse(stores.servicesOptIn.getCustomServicesEnabled())
        assertFalse(stores.scheduleChecks.getScheduleSportAutoSignEnabled())
        assertTrue(stores.scheduleChecks.getScheduleChangesEnabled())
        assertEquals(
            ScheduleWidgetSettings(
                compact = CompactScheduleWidgetSettings(
                    showNextLessonEarly = true,
                    hideTeacher = false,
                    textSize = WidgetTextSize.NORMAL
                ),
                full = FullScheduleWidgetSettings(
                    hideTeacher = false,
                    hidePastLessons = false,
                    showTomorrowWhenTodayIsOver = false,
                    textSize = WidgetTextSize.NORMAL
                )
            ),
            stores.widgetSettings.getScheduleWidgetSettings()
        )
        assertTrue(stores.qrSettings.getQrDynamicColorsEnabled())
        assertTrue(stores.qrSettings.getQrSpoilerEnabled())
        assertEquals(QrAnimationType.CIRCLE, stores.qrSettings.getQrSpoilerAnimationType())
        assertTrue(stores.sportSignSelectors.getSportSignHideTeacherSelectorEnabled())
        assertTrue(stores.sportSignSelectors.getSportSignHideTimeSelectorEnabled())
        assertTrue(stores.markSources.getMyItmoMarksEnabled())
        assertNull(stores.markSources.getBarsMarksEnabled())
        assertTrue(stores.markSources.getSheetMarksEnabled())
        assertEquals(BarsLoginPrompt.NONE, stores.markSources.getBarsLoginPrompt())
        assertTrue(stores.homeLayout.observeDismissedHomeHints().first().isEmpty())
        assertTrue(stores.homeLayout.observeHiddenHomeCards().first().isEmpty())
        assertFalse(stores.deviceHints.observeBackgroundWorkHintShown().first())
        assertFalse(stores.deviceHints.observeQrTileAdded().first())
        assertFalse(stores.demoPreferences.getDemoActive())
    }

    private data class Key(val file: String, val type: String, val name: String)

    private fun declaredKeys(file: File): Sequence<Key> {
        val text = file.readText()
        val constants = CONSTANT.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        return KEY.findAll(text).map { match ->
            val name = TEMPLATE.replace(match.groupValues[2]) { constants[it.groupValues[1]] ?: it.value }
            Key(file.name, match.groupValues[1], name)
        }
    }

    private companion object {
        val KEY = Regex("""\b(\w+)PreferencesKey\(\s*"([^"]*)"""")
        val CONSTANT = Regex("""const val (\w+) = "([^"]*)"""")
        val TEMPLATE = Regex("""\$(\w+)""")

        val EXPECTED = listOf(
            Key("ServicesOptInPreferences.kt", "boolean", "custom_services_enabled"),
            Key("ScheduleCheckPreferences.kt", "boolean", "schedule_sport_auto_sign_enabled"),
            Key("ScheduleCheckPreferences.kt", "boolean", "schedule_changes_enabled"),
            Key("WidgetSettingsPreferences.kt", "boolean", "compact_widget_next_lesson_early"),
            Key("WidgetSettingsPreferences.kt", "boolean", "compact_widget_hide_teacher"),
            Key("WidgetSettingsPreferences.kt", "boolean", "full_widget_hide_teacher"),
            Key("WidgetSettingsPreferences.kt", "boolean", "full_widget_hide_past"),
            Key("WidgetSettingsPreferences.kt", "boolean", "full_widget_show_tomorrow"),
            Key("WidgetSettingsPreferences.kt", "string", "compact_widget_text_size"),
            Key("WidgetSettingsPreferences.kt", "string", "full_widget_text_size"),
            Key("WidgetSettingsPreferences.kt", "boolean", "widget_forward_scheduling_enabled"),
            Key("WidgetSettingsPreferences.kt", "boolean", "widget_hide_teacher_enabled"),
            Key("WidgetSettingsPreferences.kt", "boolean", "widget_hide_previous_lessons_enabled"),
            Key("WidgetSettingsPreferences.kt", "boolean", "widget_future_schedule_enabled"),
            Key("QrSettingsPreferences.kt", "boolean", "qr_dynamic_colors_enabled"),
            Key("QrSettingsPreferences.kt", "boolean", "qr_spoiler_enabled"),
            Key("QrSettingsPreferences.kt", "string", "qr_spoiler_animation_type"),
            Key("SportSignSelectorPreferences.kt", "boolean", "sport_sign_teacher_selector_enabled"),
            Key("SportSignSelectorPreferences.kt", "boolean", "sport_sign_hide_time_selector_enabled"),
            Key("MarkSourcePreferences.kt", "boolean", "myitmo_marks_enabled"),
            Key("MarkSourcePreferences.kt", "boolean", "bars_marks_enabled"),
            Key("MarkSourcePreferences.kt", "boolean", "sheet_marks_enabled"),
            Key("MarkSourcePreferences.kt", "string", "bars_marks_prompt"),
            Key("HomeLayoutPreferences.kt", "stringSet", "home_dismissed_hints"),
            Key("HomeLayoutPreferences.kt", "stringSet", "home_hidden_cards"),
            Key("DeviceHintPreferences.kt", "boolean", "background_work_hint_shown"),
            Key("DeviceHintPreferences.kt", "boolean", "qr_tile_added"),
            Key("DemoPreferences.kt", "boolean", "demo_active"),
            Key("UtilityStorage.kt", "int", "registered_firebase_owner"),
            Key("UtilityStorage.kt", "string", "registered_firebase_token"),
            Key("UtilityStorage.kt", "string", "firebase_token"),
            Key("UtilityStorage.kt", "long", "last_update_timestamp"),
            Key("UtilityStorage.kt", "boolean", "lesson_widget_style_changed"),
            Key("UtilityStorage.kt", "string", "skipped_version"),
            Key("UtilityStorage.kt", "long", "version_notification_timestamp"),
            Key("UtilityStorage.kt", "boolean", "onboarding_completed"),
            Key("PushRegistrationPreferences.kt", "string", "push_registered_token"),
            Key("PushRegistrationPreferences.kt", "int", "push_registered_owner"),
            Key("PushRegistrationPreferences.kt", "boolean", "push_registered_alerts"),
            Key("DataStoreSubjectBindingStore.kt", "string", "subject_bindings"),
            Key("BarsPreferenceRepositoryImpl.kt", "boolean", "recordbook_bars"),
            Key("DataStoreFriendSelectionHistory.kt", "string", "recent_schedule_friends"),
            Key("QrWidgetStateStoreImpl.kt", "string", "qr_widget_state_\$appWidgetId")
        )
    }
}
