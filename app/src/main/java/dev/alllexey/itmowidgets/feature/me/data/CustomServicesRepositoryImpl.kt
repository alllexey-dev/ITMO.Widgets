package dev.alllexey.itmowidgets.feature.me.data

import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.me.domain.CustomServicesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class CustomServicesRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage,
    private val identitySync: BackendIdentitySync
) : CustomServicesRepository {

    override fun observeEnabled(): Flow<Boolean> {
        return settings.observeCustomServicesEnabled()
    }

    override suspend fun setEnabled(enabled: Boolean) {
        settings.setCustomServicesEnabled(enabled)
        if (enabled) {
            // Opting in is the first moment the backend may learn who the user is;
            // without this the stored profile stays empty until the next launch.
            identitySync.sync()
        }
    }
}
