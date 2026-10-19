package dev.alllexey.itmowidgets.core.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.alllexey.itmowidgets.core.storage.DataStorePreferences

/**
 * What Backend last accepted for this installation: the push token, its owner's ISU, the alerts answer, the build
 * that registered ([appVersion], its `X-App-Version`; `null` for a registration stored before it was kept) and the
 * push channels switched off ([mutedPushes]; empty for a registration stored before they were sent).
 */
data class PushRegistration(
    val token: String,
    val ownerIsu: Int,
    val alertsAllowed: Boolean,
    val appVersion: String?,
    val mutedPushes: Set<String> = emptySet(),
) {
    override fun toString(): String =
        "PushRegistration(token=<redacted>, ownerIsu=$ownerIsu, alertsAllowed=$alertsAllowed, appVersion=$appVersion, " +
            "mutedPushes=$mutedPushes)"
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
            appVersion = preferences[APP_VERSION],
            mutedPushes = preferences[MUTED].orEmpty(),
        )
    }

    /** Replaces the stored registration; `null` forgets it (unregistered). */
    suspend fun set(registration: PushRegistration?) {
        dataStore.edit { preferences ->
            if (registration == null) {
                preferences.remove(TOKEN)
                preferences.remove(OWNER)
                preferences.remove(ALERTS)
                preferences.remove(APP_VERSION)
                preferences.remove(MUTED)
            } else {
                preferences[TOKEN] = registration.token
                preferences[OWNER] = registration.ownerIsu
                preferences[ALERTS] = registration.alertsAllowed
                val appVersion = registration.appVersion
                if (appVersion == null) preferences.remove(APP_VERSION) else preferences[APP_VERSION] = appVersion
                preferences[MUTED] = registration.mutedPushes
            }
        }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("push_registered_token")
        val OWNER = intPreferencesKey("push_registered_owner")
        val ALERTS = booleanPreferencesKey("push_registered_alerts")
        val APP_VERSION = stringPreferencesKey("push_registered_app_version")
        val MUTED = stringSetPreferencesKey("push_registered_muted")
    }
}
