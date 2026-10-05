package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow

/** The background check of the own schedule and the sport auto-sign shown in the schedule. */
class ScheduleCheckPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    suspend fun getScheduleSportAutoSignEnabled(): Boolean = read()[SCHEDULE_SPORT_AUTO_SIGN_ENABLED] ?: false

    fun observeScheduleSportAutoSignEnabled(): Flow<Boolean> =
        observe { it[SCHEDULE_SPORT_AUTO_SIGN_ENABLED] ?: false }

    suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) {
        write(SCHEDULE_SPORT_AUTO_SIGN_ENABLED, enabled)
    }

    /** The background check of the own schedule; on unless the user turned it off. */
    suspend fun getScheduleChangesEnabled(): Boolean = read()[SCHEDULE_CHANGES_ENABLED] ?: true

    fun observeScheduleChangesEnabled(): Flow<Boolean> = observe { it[SCHEDULE_CHANGES_ENABLED] ?: true }

    suspend fun setScheduleChangesEnabled(enabled: Boolean) {
        write(SCHEDULE_CHANGES_ENABLED, enabled)
    }

    private companion object {
        val SCHEDULE_SPORT_AUTO_SIGN_ENABLED = booleanPreferencesKey("schedule_sport_auto_sign_enabled")
        val SCHEDULE_CHANGES_ENABLED = booleanPreferencesKey("schedule_changes_enabled")
    }
}
