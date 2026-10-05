package dev.alllexey.itmowidgets.feature.qr.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.storage.safeEnumOf
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetState
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/** Each placed widget's reveal state under `qr_widget_state_<appWidgetId>` in the shared `app_preferences` file. */
class QrWidgetStateStoreImpl(
    private val dataStore: DataStore<Preferences>
) : QrWidgetStateStore {

    override suspend fun getState(appWidgetId: Int): QrWidgetState {
        val preferences = dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()
        return safeEnumOf(preferences[key(appWidgetId)], QrWidgetState.HIDDEN)
    }

    override suspend fun setState(appWidgetId: Int, state: QrWidgetState) {
        dataStore.edit { preferences -> preferences[key(appWidgetId)] = state.name }
    }

    override suspend fun clearState(appWidgetId: Int) {
        dataStore.edit { preferences -> preferences.remove(key(appWidgetId)) }
    }

    private fun key(appWidgetId: Int) = stringPreferencesKey("$KEY_PREFIX$appWidgetId")

    private companion object {
        const val KEY_PREFIX = "qr_widget_state_"
    }
}
