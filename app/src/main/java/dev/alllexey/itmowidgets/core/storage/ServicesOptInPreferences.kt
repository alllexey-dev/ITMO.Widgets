package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow

/** The stored ITMO.Widgets Backend opt-in; off until the user turns it on. `BackendGate` is its reader. */
class ServicesOptInPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    suspend fun getCustomServicesEnabled(): Boolean = read()[CUSTOM_SERVICES_ENABLED] ?: false

    fun observeCustomServicesEnabled(): Flow<Boolean> = observe { it[CUSTOM_SERVICES_ENABLED] ?: false }

    suspend fun setCustomServicesEnabled(enabled: Boolean) {
        write(CUSTOM_SERVICES_ENABLED, enabled)
    }

    private companion object {
        val CUSTOM_SERVICES_ENABLED = booleanPreferencesKey("custom_services_enabled")
    }
}
