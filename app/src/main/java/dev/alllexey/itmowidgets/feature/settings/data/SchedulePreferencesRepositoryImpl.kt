package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class SchedulePreferencesRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage
) : SchedulePreferencesRepository {

    override fun observeSportAutoSignEnabled(): Flow<Boolean> =
        settings.observeScheduleSportAutoSignEnabled()
}
