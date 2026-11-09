package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow

/** The switch `Общежитие и оплата`: the check's system notifications, on unless the user turned them off. */
class PaymentsCheckPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    suspend fun getNotificationsEnabled(): Boolean = read()[NOTIFICATIONS_ENABLED] ?: true

    fun observeNotificationsEnabled(): Flow<Boolean> = observe { it[NOTIFICATIONS_ENABLED] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        write(NOTIFICATIONS_ENABLED, enabled)
    }

    private companion object {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("dormitory_payments_notifications_enabled")
    }
}
