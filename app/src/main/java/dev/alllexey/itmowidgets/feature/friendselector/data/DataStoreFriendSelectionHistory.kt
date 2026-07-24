package dev.alllexey.itmowidgets.feature.friendselector.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.storage.AppPreferences
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class DataStoreFriendSelectionHistory @Inject constructor(
    @param:AppPreferences private val dataStore: DataStore<Preferences>
) : FriendSelectionHistory {

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

    private companion object {
        val RECENT_FRIENDS = stringPreferencesKey("recent_schedule_friends")
        const val SEPARATOR = ","
        const val MAX_RECENT_FRIENDS = 5
    }
}
