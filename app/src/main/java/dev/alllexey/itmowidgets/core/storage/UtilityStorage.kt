package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

class UtilityStorage(
    private val dataStore: DataStore<Preferences>,
    private val appVersionName: String
) {

    suspend fun getFirebaseToken(): String? = read()[FIREBASE_TOKEN]

    suspend fun getLastUpdateTimestamp(): Long =
        read()[LAST_UPDATE_TIMESTAMP] ?: 0L

    suspend fun getLessonWidgetStyleChanged(): Boolean =
        read()[LESSON_WIDGET_STYLE_CHANGED] ?: true

    suspend fun getVersionNotificationTimestamp(): Long =
        read()[VERSION_NOTIFICATION_TIMESTAMP] ?: 0L

    suspend fun getSkippedVersion(): String {
        return read()[SKIPPED_VERSION] ?: appVersionName
    }

    suspend fun getOnboardingCompleted(): Boolean =
        read()[ONBOARDING_COMPLETED] ?: false

    suspend fun setFirebaseToken(token: String?) {
        updateNullable(FIREBASE_TOKEN, token)
    }

    suspend fun setLastUpdateTimestamp(timestamp: Long) {
        write(LAST_UPDATE_TIMESTAMP, timestamp)
    }

    suspend fun setLessonWidgetStyleChanged(changed: Boolean) {
        write(LESSON_WIDGET_STYLE_CHANGED, changed)
    }

    suspend fun setSkippedVersion(version: String) {
        write(SKIPPED_VERSION, version)
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        write(ONBOARDING_COMPLETED, completed)
    }

    suspend fun setVersionNotificationTimestamp(notifiedAt: Long) {
        write(VERSION_NOTIFICATION_TIMESTAMP, notifiedAt)
    }

    private suspend fun read(): Preferences {
        return dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .first()
    }

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences -> preferences[key] = value }
    }

    private suspend fun <T> updateNullable(key: Preferences.Key<T>, value: T?) {
        dataStore.edit { preferences ->
            if (value == null) preferences.remove(key) else preferences[key] = value
        }
    }

    companion object {
        private val FIREBASE_TOKEN = stringPreferencesKey("firebase_token")
        private val LAST_UPDATE_TIMESTAMP = longPreferencesKey("last_update_timestamp")
        private val LESSON_WIDGET_STYLE_CHANGED =
            booleanPreferencesKey("lesson_widget_style_changed")
        private val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
        private val VERSION_NOTIFICATION_TIMESTAMP =
            longPreferencesKey("version_notification_timestamp")
        private val ONBOARDING_COMPLETED =
            booleanPreferencesKey("onboarding_completed")
    }
}
