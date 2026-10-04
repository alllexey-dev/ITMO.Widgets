package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSignDisplayOptions
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SportSignPreferencesRepositoryImpl @Inject constructor(
    private val sportSignSelectors: SportSignSelectorPreferences
) : SportSignPreferencesRepository {

    override fun observeDisplayOptions(): Flow<SportSignDisplayOptions> {
        return combine(
            sportSignSelectors.observeSportSignHideTeacherSelectorEnabled(),
            sportSignSelectors.observeSportSignHideTimeSelectorEnabled()
        ) { hideTeacher, hideTime ->
            SportSignDisplayOptions(
                hideTeacherSelector = hideTeacher,
                hideTimeSelector = hideTime
            )
        }
    }
}
