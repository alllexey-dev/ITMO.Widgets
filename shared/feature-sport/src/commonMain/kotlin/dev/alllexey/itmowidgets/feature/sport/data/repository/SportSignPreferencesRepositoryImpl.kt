package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSignDisplayOptions
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SportSignPreferencesRepositoryImpl(
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

    override fun observePinnedSections(): Flow<Set<SectionName>> =
        sportSignSelectors.observePinnedSections().map { names -> names.mapTo(mutableSetOf(), ::SectionName) }

    override suspend fun setPinned(section: SectionName, pinned: Boolean) {
        val names = sportSignSelectors.observePinnedSections().first()
        sportSignSelectors.setPinnedSections(if (pinned) names + section.raw else names - section.raw)
    }
}
