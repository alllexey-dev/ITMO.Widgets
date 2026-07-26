package dev.alllexey.itmowidgets.feature.settings.domain

import kotlinx.coroutines.flow.Flow

/**
 * Filters that the sport sign screen can show or hide.
 *
 * Stored as `hide*` to match the persisted keys, but the UI phrases them
 * positively, so the mapping happens in the presentation layer.
 */
data class SportDisplaySettings(
    val hideTeacherSelector: Boolean = true,
    val hideTimeSelector: Boolean = true
)

interface SettingsRepository {

    fun observeSportDisplaySettings(): Flow<SportDisplaySettings>

    suspend fun setTeacherSelectorHidden(hidden: Boolean)

    suspend fun setTimeSelectorHidden(hidden: Boolean)
}
