package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import kotlinx.coroutines.flow.Flow

/**
 * The app's own switch of each push category (`AppNotificationChannels.PUSHES`), on unless the user turned it off. The
 * device registration tells Backend the categories that are off ([mutedCategoriesOf]), so it stops sending them; the
 * client still drops what arrives of them (an older Backend, the sport queue pushes the device books on). A device
 * setting that sign-out keeps.
 */
class PushCategoryPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    suspend fun isEnabled(channel: String): Boolean = read()[keyOf(channel)] ?: true

    /** The push channels the user turned off. */
    fun observeDisabled(): Flow<Set<String>> = observe { it.disabled() }

    suspend fun disabled(): Set<String> = read().disabled()

    suspend fun setEnabled(channel: String, enabled: Boolean) {
        write(keyOf(channel), enabled)
    }

    private fun Preferences.disabled(): Set<String> = KEYS.filterValues { this[it] == false }.keys

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

/**
 * The wire names of `RegisterDeviceRequest.mutedPushCategories` for the switched-off [channels]: Backend's
 * `PushCategory` is the channel id in upper case. Sorted, so an unchanged set compares equal; `null` for none.
 */
fun mutedCategoriesOf(channels: Set<String>): List<String>? = channels.map { it.uppercase() }.sorted().takeIf { it.isNotEmpty() }
