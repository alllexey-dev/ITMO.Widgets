package dev.alllexey.itmowidgets.feature.home.data

import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.util.safeEnumOf
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreHomeCardPreferences @Inject constructor(
    private val storage: AppSettingsStorage
) : HomeCardPreferences {

    override fun observeHidden(): Flow<Set<HomeCardKind>> =
        storage.observeHiddenHomeCards().map { names -> names.mapNotNull { safeEnumOf<HomeCardKind>(it) }.toSet() }
}
