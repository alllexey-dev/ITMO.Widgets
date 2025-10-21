package dev.alllexey.itmowidgets.data

import android.content.SharedPreferences
import androidx.core.content.edit

class UserSettingsStorage(val prefs: SharedPreferences) {

    companion object KEYS {
        const val CUSTOM_SERVICES_ALLOW_KEY = "custom_services_allow"
        const val FIREBASE_TOKEN_KEY = "firebase_token"
        const val LAST_UPDATE_TIMESTAMP_KEY = "last_update_timestamp"
        const val SMART_SCHEDULING_KEY = "smart_scheduling"
        const val BEFOREHAND_SCHEDULING_KEY = "beforehand_scheduling"
        const val SINGLE_LESSON_WIDGET_STYLE = "single_lesson_widget_style"
        const val LIST_LESSON_WIDGET_STYLE = "list_lesson_widget_style"
        const val DOT_STYLE = "dot"
        const val LINE_STYLE = "line"
        const val HIDE_TEACHER_KEY = "hide_teacher"
        const val HIDE_PREVIOUS_LESSONS_KEY = "hide_previous_lessons"
        const val DYNAMIC_QR_COLORS_KEY = "dynamic_qr_colors"
        const val ERROR_LOG_KEY = "error_log"
        const val LESSON_WIDGET_STYLE_CHANGED_KEY = "lesson_widget_style_changed"
    }

    fun getBackendAllow(): Boolean {
        return prefs.getBoolean(CUSTOM_SERVICES_ALLOW_KEY, false)
    }

    fun getFirebaseToken(): String? {
        return prefs.getString(FIREBASE_TOKEN_KEY, null)
    }

    fun getLastUpdateTimestamp(): Long {
        return prefs.getLong(LAST_UPDATE_TIMESTAMP_KEY, 0)
    }

    fun getSmartSchedulingState(): Boolean {
        return prefs.getBoolean(SMART_SCHEDULING_KEY, true)
    }

    fun getBeforehandSchedulingState(): Boolean {
        return prefs.getBoolean(BEFOREHAND_SCHEDULING_KEY, true)
    }

    fun getSingleLessonWidgetStyle(): String? {
        return prefs.getString(SINGLE_LESSON_WIDGET_STYLE, DOT_STYLE)
    }

    fun getLessonListWidgetStyle(): String? {
        return prefs.getString(LIST_LESSON_WIDGET_STYLE, DOT_STYLE)
    }

    fun getHideTeacherState(): Boolean {
        return prefs.getBoolean(HIDE_TEACHER_KEY, false)
    }

    fun getHidePreviousLessonsState(): Boolean {
        return prefs.getBoolean(HIDE_PREVIOUS_LESSONS_KEY, false)
    }

    fun getDynamicQrColorsState(): Boolean {
        return prefs.getBoolean(DYNAMIC_QR_COLORS_KEY, true)
    }

    fun getErrorLog(): String? {
        return prefs.getString(ERROR_LOG_KEY, "empty")
    }

    fun getLessonWidgetStyleChanged(): Boolean {
        return prefs.getBoolean(LESSON_WIDGET_STYLE_CHANGED_KEY, true)
    }

    fun setBackendAllow(allow: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(CUSTOM_SERVICES_ALLOW_KEY, allow)
        }
    }

    fun setFirebaseToken(token: String?) {
        prefs.edit(commit = true) {
            putString(FIREBASE_TOKEN_KEY, token)
        }
    }

    fun setLastUpdateTimestamp(timestamp: Long) {
        prefs.edit(commit = true) {
            putLong(LAST_UPDATE_TIMESTAMP_KEY, timestamp)
        }
    }

    fun setSmartSchedulingState(smartScheduling: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(SMART_SCHEDULING_KEY, smartScheduling)
        }
    }

    fun setBeforehandSchedulingState(beforehandScheduling: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(BEFOREHAND_SCHEDULING_KEY, beforehandScheduling)
        }
    }

    fun setSingleLessonWidgetStyle(style: String?) {
        prefs.edit(commit = true) {
            putString(SINGLE_LESSON_WIDGET_STYLE, style)
        }
    }

    fun setListLessonWidgetStyle(style: String?) {
        prefs.edit(commit = true) {
            putString(LIST_LESSON_WIDGET_STYLE, style)
        }
    }

    fun setHideTeacherState(hideTeacher: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(HIDE_TEACHER_KEY, hideTeacher)
        }
    }

    fun setHidePreviousLessonsState(hidePreviousLessons: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(HIDE_PREVIOUS_LESSONS_KEY, hidePreviousLessons)
        }
    }

    fun setDynamicQrColorsState(dynamicQrColors: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(DYNAMIC_QR_COLORS_KEY, dynamicQrColors)
        }
    }

    fun setErrorLog(errorLog: String?) {
        prefs.edit(commit = true) {
            putString(ERROR_LOG_KEY, errorLog)
        }
    }

    fun setLessonWidgetStyleChanged(lessonWidgetStyleChanged: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(LESSON_WIDGET_STYLE_CHANGED_KEY, lessonWidgetStyleChanged)
        }
    }
}