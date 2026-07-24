package dev.alllexey.itmowidgets.feature.me.data

import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.me.domain.CustomServicesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class CustomServicesRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage
) : CustomServicesRepository {

    override fun observeEnabled(): Flow<Boolean> {
        return settings.observeCustomServicesEnabled()
    }

    override suspend fun setEnabled(enabled: Boolean) {
        settings.setCustomServicesEnabled(enabled)
    }
}
