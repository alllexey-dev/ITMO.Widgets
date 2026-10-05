package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow

/** The QR tile and background work hints; flags of the device that sign-out keeps. */
class DeviceHintPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    /** Whether the one-time dialog about background work was offered. */
    fun observeBackgroundWorkHintShown(): Flow<Boolean> = observe { it[BACKGROUND_WORK_HINT_SHOWN] ?: false }

    suspend fun setBackgroundWorkHintShown() {
        write(BACKGROUND_WORK_HINT_SHOWN, true)
    }

    /** Whether the QR pass tile is in the quick settings, as far as the app saw. */
    fun observeQrTileAdded(): Flow<Boolean> = observe { it[QR_TILE_ADDED] ?: false }

    suspend fun setQrTileAdded(added: Boolean) {
        write(QR_TILE_ADDED, added)
    }

    private companion object {
        val BACKGROUND_WORK_HINT_SHOWN = booleanPreferencesKey("background_work_hint_shown")
        val QR_TILE_ADDED = booleanPreferencesKey("qr_tile_added")
    }
}
