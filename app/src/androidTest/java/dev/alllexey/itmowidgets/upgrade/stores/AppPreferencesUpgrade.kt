package dev.alllexey.itmowidgets.upgrade.stores

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.feature.friendselector.data.DataStoreFriendSelectionHistory
import dev.alllexey.itmowidgets.feature.qr.data.QrWidgetStateStoreImpl
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetState
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.DataStoreSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.testing.DeviceDispatchers
import dev.alllexey.itmowidgets.upgrade.Captured22
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * `files/datastore/app_preferences.preferences_pb`: all 40 keys 2.2 wrote (28 of `AppSettingsStorage`, now split
 * into the per-concern stores, 8 of `UtilityStorage` and one each of the subject bindings, the BARS switch, a QR
 * widget's state and the recent friends) keep their names, types and values and read through head stores.
 */
object AppPreferencesUpgrade {

    fun check(fixture: Upgrade22Fixture): Unit = runBlocking {
        val preferences = fixture.preferences
        assertEquals(EXPECTED.asMap(), preferences.data.first().asMap())

        assertTrue(ServicesOptInPreferences(preferences).getCustomServicesEnabled())
        ScheduleCheckPreferences(preferences).let {
            assertTrue(it.getScheduleSportAutoSignEnabled())
            assertFalse(it.getScheduleChangesEnabled())
        }
        // The per-format keys win over the legacy shared keys, which hold the opposite values.
        assertEquals(
            ScheduleWidgetSettings(
                compact = CompactScheduleWidgetSettings(
                    showNextLessonEarly = true,
                    hideTeacher = false,
                    textSize = WidgetTextSize.LARGE
                ),
                full = FullScheduleWidgetSettings(
                    hideTeacher = true,
                    hidePastLessons = false,
                    showTomorrowWhenTodayIsOver = false,
                    textSize = WidgetTextSize.EXTRA_LARGE
                )
            ),
            WidgetSettingsPreferences(preferences).getScheduleWidgetSettings()
        )
        QrSettingsPreferences(preferences).let {
            assertFalse(it.getQrDynamicColorsEnabled())
            assertFalse(it.getQrSpoilerEnabled())
            assertEquals(QrAnimationType.FADE, it.getQrSpoilerAnimationType())
        }
        SportSignSelectorPreferences(preferences).let {
            assertFalse(it.getSportSignHideTeacherSelectorEnabled())
            assertFalse(it.getSportSignHideTimeSelectorEnabled())
        }
        MarkSourcePreferences(preferences).let {
            assertFalse(it.getMyItmoMarksEnabled())
            assertFalse(it.getSheetMarksEnabled())
            assertEquals(false, it.getBarsMarksEnabled())
            assertEquals(BarsLoginPrompt.SHOWN, it.getBarsLoginPrompt())
        }
        DeviceHintPreferences(preferences).let {
            assertTrue(it.observeBackgroundWorkHintShown().first())
            assertTrue(it.observeQrTileAdded().first())
        }
        HomeLayoutPreferences(preferences).let {
            assertEquals(setOf("WIDGETS", "SERVICES"), it.observeDismissedHomeHints().first())
            assertEquals(setOf("SPORT", "FRIEND_REQUESTS"), it.observeHiddenHomeCards().first())
        }
        assertTrue(DemoPreferences(preferences).getDemoActive())

        UtilityStorage(preferences, appVersionName = "head").let {
            assertEquals("upgrade22-registered-fcm-token", it.getRegisteredFirebaseToken())
            assertEquals(Captured22.ISU, it.getRegisteredFirebaseOwner())
            assertEquals("upgrade22-fcm-token", it.getFirebaseToken())
            assertEquals("2.1.1", it.getSkippedVersion())
            assertEquals(Captured22.AT_MS - 7_200_000, it.getVersionNotificationTimestamp())
            assertTrue(it.getOnboardingCompleted())
        }

        DataStoreSubjectBindingStore(preferences, DeviceDispatchers).let {
            assertEquals(2001L, it.get(1001))
            assertEquals(2002L, it.get(1002))
        }
        val unusedBarsTokens = BarsTokenStore(File(fixture.cacheDir, "unused_bars_tokens.enc"), NoCipher)
        val barsPreference = BarsPreferenceRepositoryImpl(
            unusedBarsTokens,
            preferences,
            MarkSourcePreferences(preferences),
            DeviceDispatchers
        )
        assertTrue(barsPreference.isEnabled())
        assertEquals(QrWidgetState.VISIBLE, QrWidgetStateStoreImpl(preferences).getState(42))
        assertEquals(listOf(100003, 100002), DataStoreFriendSelectionHistory(preferences).getRecentIsu())

        // Without the per-format keys, the legacy shared keys 2.0 and 2.1 wrote still apply to both formats.
        preferences.edit { stored -> PER_FORMAT_FLAGS.forEach { stored.remove(it) } }
        assertEquals(
            ScheduleWidgetSettings(
                compact = CompactScheduleWidgetSettings(
                    showNextLessonEarly = false,
                    hideTeacher = true,
                    textSize = WidgetTextSize.LARGE
                ),
                full = FullScheduleWidgetSettings(
                    hideTeacher = true,
                    hidePastLessons = true,
                    showTomorrowWhenTodayIsOver = true,
                    textSize = WidgetTextSize.EXTRA_LARGE
                )
            ),
            WidgetSettingsPreferences(preferences).getScheduleWidgetSettings()
        )
    }

    private val PER_FORMAT_FLAGS = listOf(
        "compact_widget_next_lesson_early",
        "compact_widget_hide_teacher",
        "full_widget_hide_teacher",
        "full_widget_hide_past",
        "full_widget_show_tomorrow"
    ).map(::booleanPreferencesKey)

    private val EXPECTED: Preferences = preferencesOf(
        // AppSettingsStorage at 2.2 (28)
        booleanPreferencesKey("custom_services_enabled") to true,
        booleanPreferencesKey("schedule_sport_auto_sign_enabled") to true,
        booleanPreferencesKey("schedule_changes_enabled") to false,
        booleanPreferencesKey("compact_widget_next_lesson_early") to true,
        booleanPreferencesKey("compact_widget_hide_teacher") to false,
        booleanPreferencesKey("full_widget_hide_teacher") to true,
        booleanPreferencesKey("full_widget_hide_past") to false,
        booleanPreferencesKey("full_widget_show_tomorrow") to false,
        stringPreferencesKey("compact_widget_text_size") to "LARGE",
        stringPreferencesKey("full_widget_text_size") to "EXTRA_LARGE",
        booleanPreferencesKey("widget_forward_scheduling_enabled") to false,
        booleanPreferencesKey("widget_hide_teacher_enabled") to true,
        booleanPreferencesKey("widget_hide_previous_lessons_enabled") to true,
        booleanPreferencesKey("widget_future_schedule_enabled") to true,
        booleanPreferencesKey("qr_dynamic_colors_enabled") to false,
        booleanPreferencesKey("qr_spoiler_enabled") to false,
        stringPreferencesKey("qr_spoiler_animation_type") to "FADE",
        booleanPreferencesKey("sport_sign_teacher_selector_enabled") to false,
        booleanPreferencesKey("sport_sign_hide_time_selector_enabled") to false,
        booleanPreferencesKey("myitmo_marks_enabled") to false,
        booleanPreferencesKey("sheet_marks_enabled") to false,
        booleanPreferencesKey("bars_marks_enabled") to false,
        stringPreferencesKey("bars_marks_prompt") to "SHOWN",
        booleanPreferencesKey("background_work_hint_shown") to true,
        booleanPreferencesKey("qr_tile_added") to true,
        stringSetPreferencesKey("home_dismissed_hints") to setOf("WIDGETS", "SERVICES"),
        stringSetPreferencesKey("home_hidden_cards") to setOf("SPORT", "FRIEND_REQUESTS"),
        booleanPreferencesKey("demo_active") to true,
        // UtilityStorage (8)
        stringPreferencesKey("registered_firebase_token") to "upgrade22-registered-fcm-token",
        intPreferencesKey("registered_firebase_owner") to Captured22.ISU,
        stringPreferencesKey("firebase_token") to "upgrade22-fcm-token",
        longPreferencesKey("last_update_timestamp") to Captured22.AT_MS - 3_600_000,
        booleanPreferencesKey("lesson_widget_style_changed") to false,
        stringPreferencesKey("skipped_version") to "2.1.1",
        longPreferencesKey("version_notification_timestamp") to Captured22.AT_MS - 7_200_000,
        booleanPreferencesKey("onboarding_completed") to true,
        // One each: subject bindings, the BARS switch, a QR widget's state, the recent friends
        stringPreferencesKey("subject_bindings") to "1001:2001,1002:2002",
        booleanPreferencesKey("recordbook_bars") to true,
        stringPreferencesKey("qr_widget_state_42") to "VISIBLE",
        stringPreferencesKey("recent_schedule_friends") to "100003,100002"
    )

    private object NoCipher : TokenCipher {
        override fun encrypt(value: String) = error("BARS tokens are not written here")
        override fun decrypt(value: String) = error("BARS tokens are not read here")
    }
}
