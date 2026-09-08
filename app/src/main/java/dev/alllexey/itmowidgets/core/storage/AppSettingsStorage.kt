package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
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

    suspend fun getWidgetForwardSchedulingEnabled(): Boolean =
        read()[WIDGET_FORWARD_SCHEDULING_ENABLED] ?: true

    suspend fun getSingleLessonWidgetStyle(): LessonStyle = LessonStyle.DOT

    suspend fun getLessonListWidgetStyle(): LessonStyle = LessonStyle.DOT

    suspend fun getWidgetHideTeacherEnabled(): Boolean =
        read()[WIDGET_HIDE_TEACHER_ENABLED] ?: false

    suspend fun getWidgetHidePreviousLessonsEnabled(): Boolean =
        read()[WIDGET_HIDE_PREVIOUS_LESSONS_ENABLED] ?: false

    suspend fun getWidgetFutureScheduleEnabled(): Boolean =
        read()[WIDGET_FUTURE_SCHEDULE_ENABLED] ?: false

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

    fun observeWidgetForwardSchedulingEnabled(): Flow<Boolean> =
        preferences
            .map { it[WIDGET_FORWARD_SCHEDULING_ENABLED] ?: true }
            .distinctUntilChanged()

    fun observeWidgetHideTeacherEnabled(): Flow<Boolean> =
        preferences
            .map { it[WIDGET_HIDE_TEACHER_ENABLED] ?: false }
            .distinctUntilChanged()

    fun observeWidgetHidePreviousLessonsEnabled(): Flow<Boolean> =
        preferences
            .map { it[WIDGET_HIDE_PREVIOUS_LESSONS_ENABLED] ?: false }
            .distinctUntilChanged()

    fun observeWidgetFutureScheduleEnabled(): Flow<Boolean> =
        preferences
            .map { it[WIDGET_FUTURE_SCHEDULE_ENABLED] ?: false }
            .distinctUntilChanged()

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

    suspend fun setWidgetForwardSchedulingEnabled(enabled: Boolean) {
        write(WIDGET_FORWARD_SCHEDULING_ENABLED, enabled)
    }

    suspend fun setWidgetHideTeacherEnabled(enabled: Boolean) {
        write(WIDGET_HIDE_TEACHER_ENABLED, enabled)
    }

    suspend fun setWidgetHidePreviousLessonsEnabled(enabled: Boolean) {
        write(WIDGET_HIDE_PREVIOUS_LESSONS_ENABLED, enabled)
    }

    suspend fun setWidgetFutureScheduleEnabled(enabled: Boolean) {
        write(WIDGET_FUTURE_SCHEDULE_ENABLED, enabled)
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
