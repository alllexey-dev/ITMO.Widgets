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

    suspend fun getWidgetSmartSchedulingEnabled(): Boolean =
        read()[WIDGET_SMART_SCHEDULING_ENABLED] ?: true

    suspend fun getWidgetForwardSchedulingEnabled(): Boolean =
        read()[WIDGET_FORWARD_SCHEDULING_ENABLED] ?: true

    suspend fun getSingleLessonWidgetStyle(): LessonStyle {
        return safeEnumOf(read()[SINGLE_LESSON_WIDGET_STYLE], LessonStyle.DOT)
    }

    suspend fun getLessonListWidgetStyle(): LessonStyle {
        return safeEnumOf(read()[LIST_LESSON_WIDGET_STYLE], LessonStyle.DOT)
    }

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

    suspend fun setWidgetSmartSchedulingEnabled(enabled: Boolean) {
        write(WIDGET_SMART_SCHEDULING_ENABLED, enabled)
    }

    suspend fun setWidgetForwardSchedulingEnabled(enabled: Boolean) {
        write(WIDGET_FORWARD_SCHEDULING_ENABLED, enabled)
    }

    suspend fun setSingleLessonWidgetStyle(style: LessonStyle) {
        write(SINGLE_LESSON_WIDGET_STYLE, style.name)
    }

    suspend fun setListLessonWidgetStyle(style: LessonStyle) {
        write(LIST_LESSON_WIDGET_STYLE, style.name)
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
        private val SINGLE_LESSON_WIDGET_STYLE =
            stringPreferencesKey("single_lesson_widget_style")
        private val LIST_LESSON_WIDGET_STYLE =
            stringPreferencesKey("list_lesson_widget_style")
        private val WIDGET_SMART_SCHEDULING_ENABLED =
            booleanPreferencesKey("widget_smart_scheduling_enabled")
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
