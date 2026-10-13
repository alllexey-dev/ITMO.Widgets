package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSignDisplayOptions
import kotlinx.coroutines.flow.Flow

interface SportSignPreferencesRepository {
    fun observeDisplayOptions(): Flow<SportSignDisplayOptions>

    /** The sections pinned to «Мои секции», kept on the device. */
    fun observePinnedSections(): Flow<Set<SectionName>>

    suspend fun setPinned(section: SectionName, pinned: Boolean)
}
