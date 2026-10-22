package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.alllexey.itmowidgets.core.connections.SignInLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Device flags of «Устройства» that sign-out keeps: the sign-in label switch ([SignInLabel], on by default) and the
 * client sign-ins whose 7-day reminder was already posted (`<connection id>:<end instant>`, so a renewed sign-in
 * reminds again).
 */
class SignInLabelPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore), SignInLabel {

    override fun observeBrowserLike(): Flow<Boolean> = observe { it[BROWSER_LIKE] ?: true }

    override suspend fun isBrowserLike(): Boolean = observeBrowserLike().first()

    override suspend fun setBrowserLike(browserLike: Boolean) {
        write(BROWSER_LIKE, browserLike)
    }

    suspend fun reminded(): Set<String> = read()[REMINDED].orEmpty()

    /** Keeps only [keys] of the current clients plus [key], so the set never grows past them. */
    suspend fun addReminded(key: String, keys: Set<String>) {
        dataStore.edit { it[REMINDED] = it[REMINDED].orEmpty().intersect(keys) + key }
    }

    private companion object {
        val BROWSER_LIKE = booleanPreferencesKey("sign_in_browser_like")
        val REMINDED = stringSetPreferencesKey("connection_reminders")
    }
}
