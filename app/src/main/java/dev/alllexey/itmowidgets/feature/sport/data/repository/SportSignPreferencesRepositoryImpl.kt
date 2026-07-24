package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSignDisplayOptions
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SportSignPreferencesRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage
) : SportSignPreferencesRepository {

    override fun observeDisplayOptions(): Flow<SportSignDisplayOptions> {
        return combine(
            settings.observeSportSignHideTeacherSelectorEnabled(),
            settings.observeSportSignHideTimeSelectorEnabled()
        ) { hideTeacher, hideTime ->
            SportSignDisplayOptions(
                hideTeacherSelector = hideTeacher,
                hideTimeSelector = hideTime
            )
        }
    }
}
