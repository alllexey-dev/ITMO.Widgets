package dev.alllexey.itmowidgets.feature.home.data

import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.storage.safeEnumOf
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreHomeCardPreferences(
    private val homeLayout: HomeLayoutPreferences
) : HomeCardPreferences {

    override fun observeHidden(): Flow<Set<HomeCardKind>> =
        homeLayout.observeHiddenHomeCards().map { names -> names.mapNotNull { safeEnumOf<HomeCardKind>(it) }.toSet() }
}
