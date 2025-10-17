package me.alllexey123.itmowidgets.data

import android.content.SharedPreferences
import api.myitmo.storage.Storage
import androidx.core.content.edit
import me.alllexey123.itmowidgets.api.BackendStorage

const val ACCESS_TOKEN_KEY = "access_token"
const val ACCESS_TOKEN_EXPIRES_KEY = ACCESS_TOKEN_KEY + "_expires"
const val REFRESH_TOKEN_KEY = "refresh_token"
const val REFRESH_TOKEN_EXPIRES_KEY = REFRESH_TOKEN_KEY + "_expires"
const val BACKEND_ACCESS_TOKEN_KEY = "backend_access_token"
const val BACKEND_ACCESS_TOKEN_EXPIRES_KEY = BACKEND_ACCESS_TOKEN_KEY + "_expires"
const val BACKEND_REFRESH_TOKEN_KEY = "backend_refresh_token"
const val BACKEND_REFRESH_TOKEN_EXPIRES_KEY = BACKEND_REFRESH_TOKEN_KEY + "_expires"
const val BACKEND_ALLOW_KEY = "backend_allow"
const val FIREBASE_TOKEN_KEY = "firebase_token"
const val LAST_UPDATE_TIMESTAMP_KEY = "last_update_timestamp"
const val ID_TOKEN_KEY = "id_token"
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

class PreferencesStorage(val prefs: SharedPreferences) : Storage, BackendStorage {

    override fun getAccessToken(): String? {
        return prefs.getString(ACCESS_TOKEN_KEY, null)
    }

    override fun getAccessExpiresAt(): Long {
        return prefs.getLong(ACCESS_TOKEN_EXPIRES_KEY, 0)
    }

    override fun getRefreshToken(): String? {
        return prefs.getString(REFRESH_TOKEN_KEY, null)
    }

    override fun getRefreshExpiresAt(): Long {
        return prefs.getLong(REFRESH_TOKEN_EXPIRES_KEY, 0)
    }

    override fun getIdToken(): String? {
        return prefs.getString(ID_TOKEN_KEY, null)
    }

    override fun getBackendAccessToken(): String? {
        return prefs.getString(BACKEND_ACCESS_TOKEN_KEY, null)
    }

    override fun getBackendAccessExpiresAt(): Long {
        return prefs.getLong(BACKEND_ACCESS_TOKEN_EXPIRES_KEY, 0)
    }

    override fun getBackendRefreshToken(): String? {
        return prefs.getString(BACKEND_REFRESH_TOKEN_KEY, null)
    }

    override fun getBackendRefreshExpiresAt(): Long {
        return prefs.getLong(BACKEND_REFRESH_TOKEN_EXPIRES_KEY, 0)
    }

    override fun getBackendAllow(): Boolean {
        return prefs.getBoolean(BACKEND_ALLOW_KEY, false)
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

    override fun setAccessToken(accessToken: String?) {
        prefs.edit {
            putString(ACCESS_TOKEN_KEY, accessToken)
        }
    }

    override fun setAccessExpiresAt(accessExpiresAt: Long) {
        prefs.edit {
            putLong(ACCESS_TOKEN_EXPIRES_KEY, accessExpiresAt)
        }
    }

    override fun setRefreshToken(refreshToken: String?) {
        prefs.edit {
            putString(REFRESH_TOKEN_KEY, refreshToken)
        }
    }

    override fun setRefreshExpiresAt(refreshExpiresAt: Long) {
        prefs.edit {
            putLong(REFRESH_TOKEN_EXPIRES_KEY, refreshExpiresAt)
        }
    }

    override fun setIdToken(idToken: String?) {
        prefs.edit {
            putString(ID_TOKEN_KEY, idToken)
        }
    }

    override fun setBackendAccessToken(accessToken: String?) {
        prefs.edit {
            putString(BACKEND_ACCESS_TOKEN_KEY, accessToken)
        }
    }

    override fun setBackendAccessExpiresAt(accessExpiresAt: Long) {
        prefs.edit {
            putLong(BACKEND_ACCESS_TOKEN_EXPIRES_KEY, accessExpiresAt)
        }
    }

    override fun setBackendRefreshToken(refreshToken: String?) {
        prefs.edit {
            putString(BACKEND_REFRESH_TOKEN_KEY, refreshToken)
        }
    }

    override fun setBackendRefreshExpiresAt(refreshExpiresAt: Long) {
        prefs.edit {
            putLong(BACKEND_REFRESH_TOKEN_EXPIRES_KEY, refreshExpiresAt)
        }
    }

    override fun setBackendAllow(allow: Boolean) {
        prefs.edit {
            putBoolean(BACKEND_ALLOW_KEY, allow)
        }
    }

    fun setFirebaseToken(token: String?) {
        prefs.edit() {
            putString(FIREBASE_TOKEN_KEY, token)
        }
    }

    fun setLastUpdateTimestamp(timestamp: Long) {
        prefs.edit {
            putLong(LAST_UPDATE_TIMESTAMP_KEY, timestamp)
        }
    }

    fun setSmartSchedulingState(smartScheduling: Boolean) {
        prefs.edit {
            putBoolean(SMART_SCHEDULING_KEY, smartScheduling)
        }
    }

    fun setBeforehandSchedulingState(beforehandScheduling: Boolean) {
        prefs.edit {
            putBoolean(BEFOREHAND_SCHEDULING_KEY, beforehandScheduling)
        }
    }

    fun setSingleLessonWidgetStyle(style: String?) {
        prefs.edit {
            putString(SINGLE_LESSON_WIDGET_STYLE, style)
        }
    }

    fun setListLessonWidgetStyle(style: String?) {
        prefs.edit {
            putString(LIST_LESSON_WIDGET_STYLE, style)
        }
    }

    fun setHideTeacherState(hideTeacher: Boolean) {
        prefs.edit {
            putBoolean(HIDE_TEACHER_KEY, hideTeacher)
        }
    }

    fun setHidePreviousLessonsState(hidePreviousLessons: Boolean) {
        prefs.edit {
            putBoolean(HIDE_PREVIOUS_LESSONS_KEY, hidePreviousLessons)
        }
    }

    fun setDynamicQrColorsState(dynamicQrColors: Boolean) {
        prefs.edit {
            putBoolean(DYNAMIC_QR_COLORS_KEY, dynamicQrColors)
        }
    }

    fun setErrorLog(errorLog: String?) {
        prefs.edit {
            putString(ERROR_LOG_KEY, errorLog)
        }
    }

    fun setLessonWidgetStyleChanged(lessonWidgetStyleChanged: Boolean) {
        prefs.edit {
            putBoolean(LESSON_WIDGET_STYLE_CHANGED_KEY, lessonWidgetStyleChanged)
        }
    }

}