package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SettingsRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage
) : SettingsRepository {

    override fun observeSportDisplaySettings(): Flow<SportDisplaySettings> {
        return combine(
            settings.observeSportSignHideTeacherSelectorEnabled(),
            settings.observeSportSignHideTimeSelectorEnabled()
        ) { hideTeacher, hideTime ->
            SportDisplaySettings(
                hideTeacherSelector = hideTeacher,
                hideTimeSelector = hideTime
            )
        }
    }

    override suspend fun setTeacherSelectorHidden(hidden: Boolean) {
        settings.setSportSignHideTeacherSelectorEnabled(hidden)
    }

    override suspend fun setTimeSelectorHidden(hidden: Boolean) {
        settings.setSportSignHideTimeSelectorEnabled(hidden)
    }
}
