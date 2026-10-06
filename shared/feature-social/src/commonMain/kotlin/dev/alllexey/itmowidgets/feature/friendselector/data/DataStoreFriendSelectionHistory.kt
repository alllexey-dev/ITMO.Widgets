package dev.alllexey.itmowidgets.feature.friendselector.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import kotlinx.coroutines.flow.first

/** The picker's recent friends in the one `app_preferences` DataStore, under the 2.2 key and format. */
class DataStoreFriendSelectionHistory(
    private val dataStore: DataStore<Preferences>
) : FriendSelectionHistory, SessionDataCleaner {

    override suspend fun getRecentIsu(): List<Int> {
        return dataStore.data.first()[RECENT_FRIENDS]
            ?.split(SEPARATOR)
            ?.mapNotNull(String::toIntOrNull)
            .orEmpty()
    }

    override suspend fun record(isu: Int) {
        dataStore.edit { preferences ->
            val current = preferences[RECENT_FRIENDS]
                ?.split(SEPARATOR)
                ?.mapNotNull(String::toIntOrNull)
                .orEmpty()
            preferences[RECENT_FRIENDS] = (listOf(isu) + current)
                .distinct()
                .take(MAX_RECENT_FRIENDS)
                .joinToString(SEPARATOR)
        }
    }

    override suspend fun clearSessionData() {
        dataStore.edit { preferences -> preferences.remove(RECENT_FRIENDS) }
    }

    private companion object {
        val RECENT_FRIENDS = stringPreferencesKey("recent_schedule_friends")
        const val SEPARATOR = ","
        const val MAX_RECENT_FRIENDS = 5
    }
}
