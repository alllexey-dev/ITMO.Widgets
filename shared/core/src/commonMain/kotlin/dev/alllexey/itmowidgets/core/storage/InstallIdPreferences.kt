package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import kotlin.uuid.Uuid

/**
 * The install id of `X-Client` (ADR 0034): a random UUID made on first use, never derived from hardware or
 * advertising ids. Every session change forgets it, so the next signed-in session gets a new one.
 */
class InstallIdPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore), SessionDataCleaner {

    suspend fun installId(): String {
        read()[INSTALL_ID]?.let { return it }
        // Inside one edit, so concurrent first requests agree on one id.
        val stored = dataStore.edit { if (it[INSTALL_ID] == null) it[INSTALL_ID] = Uuid.random().toString() }
        return checkNotNull(stored[INSTALL_ID])
    }

    override suspend fun clearSessionData() {
        dataStore.edit { it.remove(INSTALL_ID) }
    }

    private companion object {
        val INSTALL_ID = stringPreferencesKey("install_id")
    }
}
