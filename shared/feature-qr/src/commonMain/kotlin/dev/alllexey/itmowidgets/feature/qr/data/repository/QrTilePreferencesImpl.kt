package dev.alllexey.itmowidgets.feature.qr.data.repository

import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrTilePreferences

class QrTilePreferencesImpl(
    private val deviceHints: DeviceHintPreferences
) : QrTilePreferences {

    override suspend fun setAdded(added: Boolean) {
        deviceHints.setQrTileAdded(added)
    }
}
