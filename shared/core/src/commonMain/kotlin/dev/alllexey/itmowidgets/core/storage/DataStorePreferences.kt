package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import okio.IOException

/**
 * One concern's keys in the shared `app_preferences` file. Every store sits over the same
 * `@AppPreferences` instance; a file that cannot be read reads as empty, so every key reads as its default.
 */
abstract class DataStorePreferences(
    protected val dataStore: DataStore<Preferences>
) {

    private val preferences: Flow<Preferences> = dataStore.data.readingEmptyOnIoError()

    /**
     * androidx.datastore 1.2.1 can drop a write for a `data` collector whose first read lands inside it: that
     * read returns the old file under the write's new version, and the collector then skips the cached value
     * of that version. An identity `updateData` queues behind every write already started, so its result
     * replaces the first emission, and every later write gets a higher version and reaches the collector.
     */
    private val settledPreferences: Flow<Preferences> = flow {
        var settled = false
        dataStore.data.collect { current ->
            if (settled) {
                emit(current)
            } else {
                settled = true
                emit(dataStore.updateData { it })
            }
        }
    }.readingEmptyOnIoError()

    protected suspend fun read(): Preferences = preferences.first()

    protected fun <T> observe(value: (Preferences) -> T): Flow<T> =
        settledPreferences.map(value).distinctUntilChanged()

    protected suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun Flow<Preferences>.readingEmptyOnIoError(): Flow<Preferences> = catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
}
