package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.util.safeEnumOf
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AppSettingsStorage(
    private val dataStore: DataStore<Preferences>
) {

    private val preferences = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    suspend fun getCustomServicesEnabled(): Boolean =
        read()[CUSTOM_SERVICES_ENABLED] ?: false

    suspend fun getScheduleSportAutoSignEnabled(): Boolean =
        read()[SCHEDULE_SPORT_AUTO_SIGN_ENABLED] ?: false

    /** The background check of the own schedule; on unless the user turned it off. */
    suspend fun getScheduleChangesEnabled(): Boolean =
        read()[SCHEDULE_CHANGES_ENABLED] ?: true

    suspend fun getWidgetSmartSchedulingEnabled(): Boolean = true

    suspend fun getSingleLessonWidgetStyle(): LessonStyle = LessonStyle.DOT

    suspend fun getLessonListWidgetStyle(): LessonStyle = LessonStyle.DOT

    suspend fun getQrDynamicColorsEnabled(): Boolean =
        read()[QR_DYNAMIC_COLORS_ENABLED] ?: true

    suspend fun getQrSpoilerEnabled(): Boolean =
        read()[QR_SPOILER_ENABLED] ?: true

    suspend fun getQrSpoilerAnimationType(): QrAnimationType {
        return safeEnumOf(read()[QR_SPOILER_ANIMATION_TYPE], QrAnimationType.CIRCLE)
    }

    suspend fun getSportSignHideTeacherSelectorEnabled(): Boolean =
        read()[SPORT_SIGN_TEACHER_SELECTOR_ENABLED] ?: true

    suspend fun getSportSignHideTimeSelectorEnabled(): Boolean =
        read()[SPORT_SIGN_TIME_SELECTOR_ENABLED] ?: true

    fun observeCustomServicesEnabled(): Flow<Boolean> =
        preferences
            .map { it[CUSTOM_SERVICES_ENABLED] ?: false }
            .distinctUntilChanged()

    fun observeScheduleSportAutoSignEnabled(): Flow<Boolean> =
        preferences
            .map { it[SCHEDULE_SPORT_AUTO_SIGN_ENABLED] ?: false }
            .distinctUntilChanged()

    fun observeScheduleChangesEnabled(): Flow<Boolean> =
        preferences
            .map { it[SCHEDULE_CHANGES_ENABLED] ?: true }
            .distinctUntilChanged()

    suspend fun getScheduleWidgetSettings(): ScheduleWidgetSettings = read().scheduleWidgetSettings()

    fun observeScheduleWidgetSettings(): Flow<ScheduleWidgetSettings> =
        preferences.map { it.scheduleWidgetSettings() }.distinctUntilChanged()

    // Old shared values are read only as defaults. A format's first edit writes its own key;
    // it can never change the other format, including across app upgrades and restarts.
    private fun Preferences.scheduleWidgetSettings() = ScheduleWidgetSettings(
        compact = CompactScheduleWidgetSettings(
            showNextLessonEarly = this[COMPACT_WIDGET_NEXT_EARLY] ?: this[WIDGET_FORWARD_SCHEDULING_ENABLED] ?: true,
            hideTeacher = this[COMPACT_WIDGET_HIDE_TEACHER] ?: this[WIDGET_HIDE_TEACHER_ENABLED] ?: false,
            textSize = safeEnumOf(this[COMPACT_WIDGET_TEXT_SIZE], WidgetTextSize.NORMAL)
        ),
        full = FullScheduleWidgetSettings(
            hideTeacher = this[FULL_WIDGET_HIDE_TEACHER] ?: this[WIDGET_HIDE_TEACHER_ENABLED] ?: false,
            hidePastLessons = this[FULL_WIDGET_HIDE_PAST] ?: this[WIDGET_HIDE_PREVIOUS_LESSONS_ENABLED] ?: false,
            showTomorrowWhenTodayIsOver = this[FULL_WIDGET_SHOW_TOMORROW] ?: this[WIDGET_FUTURE_SCHEDULE_ENABLED] ?: false,
            textSize = safeEnumOf(this[FULL_WIDGET_TEXT_SIZE], WidgetTextSize.NORMAL)
        )
    )

    fun observeQrDynamicColorsEnabled(): Flow<Boolean> =
        preferences
            .map { it[QR_DYNAMIC_COLORS_ENABLED] ?: true }
            .distinctUntilChanged()

    fun observeQrSpoilerEnabled(): Flow<Boolean> =
        preferences
            .map { it[QR_SPOILER_ENABLED] ?: true }
            .distinctUntilChanged()

    fun observeQrSpoilerAnimationType(): Flow<QrAnimationType> =
        preferences
            .map { stored ->
                safeEnumOf(stored[QR_SPOILER_ANIMATION_TYPE], QrAnimationType.CIRCLE)
            }
            .distinctUntilChanged()

    fun observeSportSignHideTeacherSelectorEnabled(): Flow<Boolean> =
        preferences
            .map { it[SPORT_SIGN_TEACHER_SELECTOR_ENABLED] ?: true }
            .distinctUntilChanged()

    fun observeSportSignHideTimeSelectorEnabled(): Flow<Boolean> =
        preferences
            .map { it[SPORT_SIGN_TIME_SELECTOR_ENABLED] ?: true }
            .distinctUntilChanged()

    suspend fun setCustomServicesEnabled(enabled: Boolean) {
        write(CUSTOM_SERVICES_ENABLED, enabled)
    }

    suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) {
        write(SCHEDULE_SPORT_AUTO_SIGN_ENABLED, enabled)
    }

    suspend fun setScheduleChangesEnabled(enabled: Boolean) {
        write(SCHEDULE_CHANGES_ENABLED, enabled)
    }

    suspend fun setCompactWidgetNextLessonEarlyEnabled(enabled: Boolean) {
        write(COMPACT_WIDGET_NEXT_EARLY, enabled)
    }

    suspend fun setCompactWidgetTeacherHidden(hidden: Boolean) {
        write(COMPACT_WIDGET_HIDE_TEACHER, hidden)
    }

    suspend fun setFullWidgetTeacherHidden(hidden: Boolean) {
        write(FULL_WIDGET_HIDE_TEACHER, hidden)
    }

    suspend fun setFullWidgetPastLessonsHidden(hidden: Boolean) {
        write(FULL_WIDGET_HIDE_PAST, hidden)
    }

    suspend fun setFullWidgetTomorrowEnabled(enabled: Boolean) {
        write(FULL_WIDGET_SHOW_TOMORROW, enabled)
    }

    suspend fun setCompactWidgetTextSize(size: WidgetTextSize) {
        write(COMPACT_WIDGET_TEXT_SIZE, size.name)
    }

    suspend fun setFullWidgetTextSize(size: WidgetTextSize) {
        write(FULL_WIDGET_TEXT_SIZE, size.name)
    }

    suspend fun setQrSpoilerEnabled(enabled: Boolean) {
        write(QR_SPOILER_ENABLED, enabled)
    }

    suspend fun setQrDynamicColorsEnabled(enabled: Boolean) {
        write(QR_DYNAMIC_COLORS_ENABLED, enabled)
    }

    suspend fun setQrSpoilerAnimationType(type: QrAnimationType) {
        write(QR_SPOILER_ANIMATION_TYPE, type.name)
    }

    suspend fun setSportSignHideTeacherSelectorEnabled(enabled: Boolean) {
        write(SPORT_SIGN_TEACHER_SELECTOR_ENABLED, enabled)
    }

    suspend fun setSportSignHideTimeSelectorEnabled(enabled: Boolean) {
        write(SPORT_SIGN_TIME_SELECTOR_ENABLED, enabled)
    }

    /** The background check of My ITMO marks; on unless the user turned it off. A setting of the device. */
    suspend fun getMyItmoMarksEnabled(): Boolean = read()[MYITMO_MARKS_ENABLED] ?: true

    fun observeMyItmoMarksEnabled(): Flow<Boolean> =
        preferences.map { it[MYITMO_MARKS_ENABLED] ?: true }.distinctUntilChanged()

    suspend fun setMyItmoMarksEnabled(enabled: Boolean) {
        write(MYITMO_MARKS_ENABLED, enabled)
    }

    /** The background check of the connected sheets' totals; on unless the user turned it off. A setting of the device. */
    suspend fun getSheetMarksEnabled(): Boolean = read()[SHEET_MARKS_ENABLED] ?: true

    fun observeSheetMarksEnabled(): Flow<Boolean> =
        preferences.map { it[SHEET_MARKS_ENABLED] ?: true }.distinctUntilChanged()

    suspend fun setSheetMarksEnabled(enabled: Boolean) {
        write(SHEET_MARKS_ENABLED, enabled)
    }

    /** The background check of BARS marks; null (the switch is hidden) until the account's first BARS answer. */
    suspend fun getBarsMarksEnabled(): Boolean? = read()[BARS_MARKS_ENABLED]

    fun observeBarsMarksEnabled(): Flow<Boolean?> = preferences.map { it[BARS_MARKS_ENABLED] }.distinctUntilChanged()

    suspend fun setBarsMarksEnabled(enabled: Boolean) {
        write(BARS_MARKS_ENABLED, enabled)
    }

    /** Turns BARS marks on when the user has never decided; true when this call changed it. One transaction. */
    suspend fun enableBarsMarksIfUnset(): Boolean {
        var changed = false
        dataStore.edit {
            if (it[BARS_MARKS_ENABLED] == null) {
                it[BARS_MARKS_ENABLED] = true
                changed = true
            }
        }
        return changed
    }

    /** Missing or unknown values are [BarsLoginPrompt.NONE]. */
    suspend fun getBarsLoginPrompt(): BarsLoginPrompt = safeEnumOf(read()[BARS_MARKS_PROMPT], BarsLoginPrompt.NONE)

    suspend fun setBarsLoginPrompt(prompt: BarsLoginPrompt) {
        write(BARS_MARKS_PROMPT, prompt.name)
    }

    /** Forgets the BARS switch and the sign-in prompt with the BARS session; My ITMO's switch stays. */
    suspend fun clearBarsMarkState() {
        dataStore.edit {
            it.remove(BARS_MARKS_ENABLED)
            it.remove(BARS_MARKS_PROMPT)
        }
    }

    /** Whether the one-time dialog about background work was offered; a setting of the device, sign-out keeps it. */
    fun observeBackgroundWorkHintShown(): Flow<Boolean> =
        preferences.map { it[BACKGROUND_WORK_HINT_SHOWN] ?: false }.distinctUntilChanged()

    suspend fun setBackgroundWorkHintShown() {
        write(BACKGROUND_WORK_HINT_SHOWN, true)
    }

    /** Whether the QR pass tile is in the quick settings, as far as the app saw; a flag of the device, sign-out keeps it. */
    fun observeQrTileAdded(): Flow<Boolean> =
        preferences.map { it[QR_TILE_ADDED] ?: false }.distinctUntilChanged()

    suspend fun setQrTileAdded(added: Boolean) {
        write(QR_TILE_ADDED, added)
    }

    /** Names of the home hints the user closed; the set belongs to the installation. */
    fun observeDismissedHomeHints(): Flow<Set<String>> =
        preferences.map { it[HOME_DISMISSED_HINTS].orEmpty() }.distinctUntilChanged()

    suspend fun dismissHomeHint(name: String) {
        dataStore.edit { it[HOME_DISMISSED_HINTS] = it[HOME_DISMISSED_HINTS].orEmpty() + name }
    }

    /** Names of the home card kinds hidden in settings; absent means shown. */
    fun observeHiddenHomeCards(): Flow<Set<String>> =
        preferences.map { it[HOME_HIDDEN_CARDS].orEmpty() }.distinctUntilChanged()

    suspend fun setHomeCardHidden(name: String, hidden: Boolean) {
        dataStore.edit {
            val current = it[HOME_HIDDEN_CARDS].orEmpty()
            it[HOME_HIDDEN_CARDS] = if (hidden) current + name else current - name
        }
    }

    private suspend fun read(): Preferences = preferences.first()

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences -> preferences[key] = value }
    }

    companion object {
        private val COMPACT_WIDGET_NEXT_EARLY = booleanPreferencesKey("compact_widget_next_lesson_early")
        private val COMPACT_WIDGET_HIDE_TEACHER = booleanPreferencesKey("compact_widget_hide_teacher")
        private val FULL_WIDGET_HIDE_TEACHER = booleanPreferencesKey("full_widget_hide_teacher")
        private val FULL_WIDGET_HIDE_PAST = booleanPreferencesKey("full_widget_hide_past")
        private val FULL_WIDGET_SHOW_TOMORROW = booleanPreferencesKey("full_widget_show_tomorrow")
        private val COMPACT_WIDGET_TEXT_SIZE = stringPreferencesKey("compact_widget_text_size")
        private val FULL_WIDGET_TEXT_SIZE = stringPreferencesKey("full_widget_text_size")
        private val CUSTOM_SERVICES_ENABLED =
            booleanPreferencesKey("custom_services_enabled")
        private val SCHEDULE_SPORT_AUTO_SIGN_ENABLED =
            booleanPreferencesKey("schedule_sport_auto_sign_enabled")
        private val SCHEDULE_CHANGES_ENABLED =
            booleanPreferencesKey("schedule_changes_enabled")
        private val WIDGET_FORWARD_SCHEDULING_ENABLED =
            booleanPreferencesKey("widget_forward_scheduling_enabled")
        private val WIDGET_HIDE_TEACHER_ENABLED =
            booleanPreferencesKey("widget_hide_teacher_enabled")
        private val WIDGET_HIDE_PREVIOUS_LESSONS_ENABLED =
            booleanPreferencesKey("widget_hide_previous_lessons_enabled")
        private val WIDGET_FUTURE_SCHEDULE_ENABLED =
            booleanPreferencesKey("widget_future_schedule_enabled")
        private val QR_DYNAMIC_COLORS_ENABLED =
            booleanPreferencesKey("qr_dynamic_colors_enabled")
        private val QR_SPOILER_ENABLED =
            booleanPreferencesKey("qr_spoiler_enabled")
        private val QR_SPOILER_ANIMATION_TYPE =
            stringPreferencesKey("qr_spoiler_animation_type")
        private val SPORT_SIGN_TEACHER_SELECTOR_ENABLED =
            booleanPreferencesKey("sport_sign_teacher_selector_enabled")
        private val SPORT_SIGN_TIME_SELECTOR_ENABLED =
            booleanPreferencesKey("sport_sign_hide_time_selector_enabled")
        private val MYITMO_MARKS_ENABLED = booleanPreferencesKey("myitmo_marks_enabled")
        private val BARS_MARKS_ENABLED = booleanPreferencesKey("bars_marks_enabled")
        private val SHEET_MARKS_ENABLED = booleanPreferencesKey("sheet_marks_enabled")
        private val BARS_MARKS_PROMPT = stringPreferencesKey("bars_marks_prompt")
        private val BACKGROUND_WORK_HINT_SHOWN = booleanPreferencesKey("background_work_hint_shown")
        private val QR_TILE_ADDED = booleanPreferencesKey("qr_tile_added")
        private val HOME_DISMISSED_HINTS = stringSetPreferencesKey("home_dismissed_hints")
        private val HOME_HIDDEN_CARDS = stringSetPreferencesKey("home_hidden_cards")
    }
}
