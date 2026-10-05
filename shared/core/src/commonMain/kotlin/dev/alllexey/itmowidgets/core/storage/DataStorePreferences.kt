package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.IOException

/**
 * One concern's keys in the shared `app_preferences` file. Every store sits over the same
 * `@AppPreferences` instance; a file that cannot be read reads as empty, so every key reads as its default.
 */
abstract class DataStorePreferences(
    protected val dataStore: DataStore<Preferences>
) {

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    protected suspend fun read(): Preferences = preferences.first()

    protected fun <T> observe(value: (Preferences) -> T): Flow<T> = preferences.map(value).distinctUntilChanged()

    protected suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }
}
