package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
    }
}
