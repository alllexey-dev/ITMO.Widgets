package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow

/**
 * Whether the sport sign-up hides its teacher and time selectors (both hidden by default), and the sections pinned
 * to `Мои секции` by their My ITMO names.
 */
class SportSignSelectorPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    suspend fun getSportSignHideTeacherSelectorEnabled(): Boolean = read()[SPORT_SIGN_TEACHER_SELECTOR_ENABLED] ?: true

    fun observeSportSignHideTeacherSelectorEnabled(): Flow<Boolean> =
        observe { it[SPORT_SIGN_TEACHER_SELECTOR_ENABLED] ?: true }

    suspend fun setSportSignHideTeacherSelectorEnabled(enabled: Boolean) {
        write(SPORT_SIGN_TEACHER_SELECTOR_ENABLED, enabled)
    }

    suspend fun getSportSignHideTimeSelectorEnabled(): Boolean = read()[SPORT_SIGN_TIME_SELECTOR_ENABLED] ?: true

    fun observeSportSignHideTimeSelectorEnabled(): Flow<Boolean> =
        observe { it[SPORT_SIGN_TIME_SELECTOR_ENABLED] ?: true }

    suspend fun setSportSignHideTimeSelectorEnabled(enabled: Boolean) {
        write(SPORT_SIGN_TIME_SELECTOR_ENABLED, enabled)
    }

    fun observePinnedSections(): Flow<Set<String>> = observe { it[SPORT_PINNED_SECTIONS].orEmpty() }

    suspend fun setPinnedSections(names: Set<String>) {
        dataStore.edit { it[SPORT_PINNED_SECTIONS] = names }
    }

    private companion object {
        val SPORT_SIGN_TEACHER_SELECTOR_ENABLED = booleanPreferencesKey("sport_sign_teacher_selector_enabled")
        val SPORT_SIGN_TIME_SELECTOR_ENABLED = booleanPreferencesKey("sport_sign_hide_time_selector_enabled")
        val SPORT_PINNED_SECTIONS = stringSetPreferencesKey("sport_pinned_sections")
    }
}
