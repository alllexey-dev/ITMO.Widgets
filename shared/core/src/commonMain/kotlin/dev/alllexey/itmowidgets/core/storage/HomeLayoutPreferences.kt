package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow

/** The home hints the user closed and the home cards hidden in settings; both belong to the installation. */
class HomeLayoutPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    /** Names of the home hints the user closed. */
    fun observeDismissedHomeHints(): Flow<Set<String>> = observe { it[HOME_DISMISSED_HINTS].orEmpty() }

    suspend fun dismissHomeHint(name: String) {
        dataStore.edit { it[HOME_DISMISSED_HINTS] = it[HOME_DISMISSED_HINTS].orEmpty() + name }
    }

    /** Brings every closed hint back; the iOS UI tests start from it (`-itmoForgetHomeHints`, Debug only). */
    suspend fun forgetDismissedHomeHints() {
        dataStore.edit { it.remove(HOME_DISMISSED_HINTS) }
    }

    /** Names of the home card kinds hidden in settings; absent means shown. */
    fun observeHiddenHomeCards(): Flow<Set<String>> = observe { it[HOME_HIDDEN_CARDS].orEmpty() }

    suspend fun setHomeCardHidden(name: String, hidden: Boolean) {
        dataStore.edit {
            val current = it[HOME_HIDDEN_CARDS].orEmpty()
            it[HOME_HIDDEN_CARDS] = if (hidden) current + name else current - name
        }
    }

    private companion object {
        val HOME_DISMISSED_HINTS = stringSetPreferencesKey("home_dismissed_hints")
        val HOME_HIDDEN_CARDS = stringSetPreferencesKey("home_hidden_cards")
    }
}
