package dev.alllexey.itmowidgets.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.safeEnumOf
import dev.alllexey.itmowidgets.feature.qr.ui.QrWidgetState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class UtilityStorage @Inject constructor(
    val prefs: SharedPreferences,
    @param:ApplicationContext val context: Context
) {

    companion object KEYS {
        const val FIREBASE_TOKEN_KEY = "firebase_token"
        const val LAST_UPDATE_TIMESTAMP_KEY = "last_update_timestamp"
        const val QR_WIDGET_STATE_PREFIX = "qr_widget_state_"
        const val LESSON_WIDGET_STYLE_CHANGED_KEY = "lesson_widget_style_changed"
        const val SKIPPED_VERSION_KEY = "skipped_version"
        const val VERSION_NOTIFICATION_TIMESTAMP_KEY = "version_notification_timestamp"
        const val ONBOARDING_COMPLETED_KEY = "onboarding_completed"
    }

    // region getters

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
        val stateName = prefs.getString("$QR_WIDGET_STATE_PREFIX$appWidgetId", QrWidgetState.HIDDEN.name)
        return safeEnumOf(stateName, QrWidgetState.HIDDEN)
    }

    fun getVersionNotificationTimestamp(): Long {
        return prefs.getLong(VERSION_NOTIFICATION_TIMESTAMP_KEY, 0L)
    }

    fun getSkippedVersion(): String {
        return prefs.getString(SKIPPED_VERSION_KEY, null) ?: ContextCompat.getString(context, R.string.app_version)
    }

    fun getOnboardingCompleted(): Boolean {
        return prefs.getBoolean(ONBOARDING_COMPLETED_KEY, false)
    }

    // endregion getters

    // region setters

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

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(ONBOARDING_COMPLETED_KEY, completed)
        }
    }

    fun setVersionNotificationTimestamp(notifiedAt: Long) {
        prefs.edit(commit = true) {
            putLong(VERSION_NOTIFICATION_TIMESTAMP_KEY, notifiedAt)
        }
    }

    // endregion setters
}
