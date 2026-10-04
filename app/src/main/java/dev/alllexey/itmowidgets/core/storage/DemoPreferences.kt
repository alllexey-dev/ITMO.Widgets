package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow

/** The hidden demo session; survives process death so the session comes back as demo. */
class DemoPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    suspend fun getDemoActive(): Boolean = read()[DEMO_ACTIVE] ?: false

    fun observeDemoActive(): Flow<Boolean> = observe { it[DEMO_ACTIVE] ?: false }

    suspend fun setDemoActive(active: Boolean) {
        write(DEMO_ACTIVE, active)
    }

    private companion object {
        val DEMO_ACTIVE = booleanPreferencesKey("demo_active")
    }
}
