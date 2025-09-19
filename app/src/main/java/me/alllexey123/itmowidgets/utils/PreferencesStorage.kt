package me.alllexey123.itmowidgets.utils

import android.content.SharedPreferences
import api.myitmo.storage.Storage
import androidx.core.content.edit

const val ACCESS_TOKEN_KEY = "access_token"
const val ACCESS_TOKEN_EXPIRES_KEY = ACCESS_TOKEN_KEY + "_expires"
const val REFRESH_TOKEN_KEY = "refresh_token"
const val REFRESH_TOKEN_EXPIRES_KEY = REFRESH_TOKEN_KEY + "_expires"
const val LAST_UPDATE_TIMESTAMP_KEY = "last_update_timestamp"
const val ID_TOKEN_KEY = "id_token"

const val SMART_SCHEDULING_KEY = "smart_scheduling"

const val BEFOREHAND_SCHEDULING_KEY = "beforehand_scheduling"

const val SINGLE_LESSON_WIDGET_STYLE = "single_lesson_widget_style"

const val LIST_LESSON_WIDGET_STYLE = "list_lesson_widget_style"

const val DOT_STYLE = "dot"

const val LINE_STYLE = "line"

class PreferencesStorage(val prefs: SharedPreferences) : Storage {

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

    fun getListLessonWidgetStyle(): String? {
        return prefs.getString(LIST_LESSON_WIDGET_STYLE, DOT_STYLE)
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

}