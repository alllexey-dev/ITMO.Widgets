package dev.alllexey.itmowidgets.core.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.storage.DataStorePreferences

/** What Backend last accepted for this installation: the push token, its owner's ISU and the alerts answer. */
data class PushRegistration(val token: String, val ownerIsu: Int, val alertsAllowed: Boolean) {
    override fun toString(): String =
        "PushRegistration(token=<redacted>, ownerIsu=$ownerIsu, alertsAllowed=$alertsAllowed)"
}

/**
 * The last [PushRegistration] Backend accepted, so a sync registers again only when one of its fields changed.
 * Keys of their own beside `UtilityStorage`'s Android registration keys, which iOS never wrote.
 */
class PushRegistrationPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    suspend fun get(): PushRegistration? {
        val preferences = read()
        return PushRegistration(
            token = preferences[TOKEN] ?: return null,
            ownerIsu = preferences[OWNER] ?: return null,
            alertsAllowed = preferences[ALERTS] ?: return null,
        )
    }

    /** Replaces the stored registration; `null` forgets it (unregistered). */
    suspend fun set(registration: PushRegistration?) {
        dataStore.edit { preferences ->
            if (registration == null) {
                preferences.remove(TOKEN)
                preferences.remove(OWNER)
                preferences.remove(ALERTS)
            } else {
                preferences[TOKEN] = registration.token
                preferences[OWNER] = registration.ownerIsu
                preferences[ALERTS] = registration.alertsAllowed
            }
        }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("push_registered_token")
        val OWNER = intPreferencesKey("push_registered_owner")
        val ALERTS = booleanPreferencesKey("push_registered_alerts")
    }
}
