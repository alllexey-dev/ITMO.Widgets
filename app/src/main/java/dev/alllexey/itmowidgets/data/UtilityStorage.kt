package dev.alllexey.itmowidgets.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.widgets.data.QrWidgetState

class UtilityStorage(val prefs: SharedPreferences, val context: Context) {

    companion object KEYS {
        const val FIREBASE_TOKEN_KEY = "firebase_token"
        const val LAST_UPDATE_TIMESTAMP_KEY = "last_update_timestamp"
        const val QR_WIDGET_STATE_PREFIX = "qr_widget_state_"
        const val LESSON_WIDGET_STYLE_CHANGED_KEY = "lesson_widget_style_changed"
        const val SKIPPED_VERSION_KEY = "skipped_version"
    }

    fun getFirebaseToken(): String? {
        return prefs.getString(FIREBASE_TOKEN_KEY, null)
    }

    fun getLastUpdateTimestamp(): Long {
        return prefs.getLong(LAST_UPDATE_TIMESTAMP_KEY, 0)
    }

    fun getLessonWidgetStyleChanged(): Boolean {
        return prefs.getBoolean(LESSON_WIDGET_STYLE_CHANGED_KEY, true)
    }

    fun getQrWidgetState(appWidgetId: Int): QrWidgetState {
        val stateName = prefs.getString("$QR_WIDGET_STATE_PREFIX$appWidgetId", QrWidgetState.SPOILER.name)
        return QrWidgetState.valueOf(stateName ?: QrWidgetState.SPOILER.name)
    }

    fun getSkippedVersion(): String {
        return prefs.getString(SKIPPED_VERSION_KEY, null) ?: ContextCompat.getString(context, R.string.app_version)
    }

    fun setQrWidgetState(appWidgetId: Int, state: QrWidgetState) {
        prefs.edit(commit = true) {
            putString("$QR_WIDGET_STATE_PREFIX$appWidgetId", state.name)
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

    fun setLessonWidgetStyleChanged(lessonWidgetStyleChanged: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(LESSON_WIDGET_STYLE_CHANGED_KEY, lessonWidgetStyleChanged)
        }
    }

    fun setSkippedVersion(skippedVersion: String) {
        prefs.edit(commit = true) {
            putString(SKIPPED_VERSION_KEY, skippedVersion)
        }
    }
}