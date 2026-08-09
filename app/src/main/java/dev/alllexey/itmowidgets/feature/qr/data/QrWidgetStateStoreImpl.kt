package dev.alllexey.itmowidgets.feature.qr.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.storage.AppPreferences
import dev.alllexey.itmowidgets.core.util.safeEnumOf
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetState
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

class QrWidgetStateStoreImpl @Inject constructor(
    @param:AppPreferences private val dataStore: DataStore<Preferences>
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
