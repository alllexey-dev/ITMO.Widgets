package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import kotlinx.coroutines.flow.Flow

/**
 * The app's own switch of each push category (`AppNotificationChannels.PUSHES`), on unless the user turned it off.
 * Backend has no per-category preference, so the client drops the notifications of a category that is off. A device
 * setting that sign-out keeps.
 */
class PushCategoryPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    suspend fun isEnabled(channel: String): Boolean = read()[keyOf(channel)] ?: true

    /** The push channels the user turned off. */
    fun observeDisabled(): Flow<Set<String>> = observe { stored -> KEYS.filterValues { stored[it] == false }.keys }

    suspend fun setEnabled(channel: String, enabled: Boolean) {
        write(keyOf(channel), enabled)
    }

    private fun keyOf(channel: String): Preferences.Key<Boolean> =
        requireNotNull(KEYS[channel]) { "Not a push channel: $channel" }

    private companion object {
        val KEYS = mapOf(
            AppNotificationChannels.SPORT to booleanPreferencesKey("sport_notifications_enabled"),
            AppNotificationChannels.FRIENDS to booleanPreferencesKey("friends_notifications_enabled"),
            AppNotificationChannels.CONNECTIONS to booleanPreferencesKey("connections_notifications_enabled"),
        )
    }
}
