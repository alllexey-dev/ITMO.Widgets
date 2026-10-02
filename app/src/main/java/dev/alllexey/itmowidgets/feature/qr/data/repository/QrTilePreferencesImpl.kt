package dev.alllexey.itmowidgets.feature.qr.data.repository

import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.qr.domain.QrTilePreferences
import javax.inject.Inject

class QrTilePreferencesImpl @Inject constructor(
    private val settings: AppSettingsStorage
) : QrTilePreferences {

    override suspend fun setAdded(added: Boolean) {
        settings.setQrTileAdded(added)
    }
}
