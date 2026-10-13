package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow

/** The ids of the bell's events the user has seen; they belong to the account, sign-out forgets them. */
class BellPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    fun observeSeen(): Flow<Set<String>> = observe { it[BELL_SEEN].orEmpty() }

    /** Adds [ids]; past [MAX_SEEN] only [ids] stay, so events gone from every source stop counting. */
    suspend fun addSeen(ids: Set<String>) {
        dataStore.edit {
            val seen = it[BELL_SEEN].orEmpty() + ids
            it[BELL_SEEN] = if (seen.size > MAX_SEEN) ids else seen
        }
    }

    suspend fun clearSeen() {
        dataStore.edit { it.remove(BELL_SEEN) }
    }

    private companion object {
        const val MAX_SEEN = 500
        val BELL_SEEN = stringSetPreferencesKey("bell_seen")
    }
}
