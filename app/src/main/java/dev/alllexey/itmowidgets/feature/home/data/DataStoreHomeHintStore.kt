package dev.alllexey.itmowidgets.feature.home.data

import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.util.safeEnumOf
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreHomeHintStore @Inject constructor(
    private val storage: AppSettingsStorage
) : HomeHintStore {

    override fun observeDismissed(): Flow<Set<HomeHint>> =
        storage.observeDismissedHomeHints().map { names -> names.mapNotNull { safeEnumOf<HomeHint>(it) }.toSet() }

    override suspend fun dismiss(hint: HomeHint) = storage.dismissHomeHint(hint.name)
}
