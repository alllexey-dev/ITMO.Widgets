package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSignDisplayOptions
import kotlinx.coroutines.flow.Flow

interface SportSignPreferencesRepository {
    fun observeDisplayOptions(): Flow<SportSignDisplayOptions>
}
