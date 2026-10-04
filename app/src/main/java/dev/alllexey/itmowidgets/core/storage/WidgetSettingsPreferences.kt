package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import kotlinx.coroutines.flow.Flow

/** The schedule widgets' display settings, one set per format. */
class WidgetSettingsPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    suspend fun getWidgetSmartSchedulingEnabled(): Boolean = true

    suspend fun getSingleLessonWidgetStyle(): LessonStyle = LessonStyle.DOT

    suspend fun getLessonListWidgetStyle(): LessonStyle = LessonStyle.DOT

    suspend fun getScheduleWidgetSettings(): ScheduleWidgetSettings = read().scheduleWidgetSettings()

    fun observeScheduleWidgetSettings(): Flow<ScheduleWidgetSettings> = observe { it.scheduleWidgetSettings() }

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

    private companion object {
        val COMPACT_WIDGET_NEXT_EARLY = booleanPreferencesKey("compact_widget_next_lesson_early")
        val COMPACT_WIDGET_HIDE_TEACHER = booleanPreferencesKey("compact_widget_hide_teacher")
        val FULL_WIDGET_HIDE_TEACHER = booleanPreferencesKey("full_widget_hide_teacher")
        val FULL_WIDGET_HIDE_PAST = booleanPreferencesKey("full_widget_hide_past")
        val FULL_WIDGET_SHOW_TOMORROW = booleanPreferencesKey("full_widget_show_tomorrow")
        val COMPACT_WIDGET_TEXT_SIZE = stringPreferencesKey("compact_widget_text_size")
        val FULL_WIDGET_TEXT_SIZE = stringPreferencesKey("full_widget_text_size")
        val WIDGET_FORWARD_SCHEDULING_ENABLED = booleanPreferencesKey("widget_forward_scheduling_enabled")
        val WIDGET_HIDE_TEACHER_ENABLED = booleanPreferencesKey("widget_hide_teacher_enabled")
        val WIDGET_HIDE_PREVIOUS_LESSONS_ENABLED = booleanPreferencesKey("widget_hide_previous_lessons_enabled")
        val WIDGET_FUTURE_SCHEDULE_ENABLED = booleanPreferencesKey("widget_future_schedule_enabled")
    }
}
