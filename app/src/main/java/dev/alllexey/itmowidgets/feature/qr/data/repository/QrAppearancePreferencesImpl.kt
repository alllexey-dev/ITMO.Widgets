package dev.alllexey.itmowidgets.feature.qr.data.repository

import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import javax.inject.Inject

class QrAppearancePreferencesImpl @Inject constructor(
    private val qrSettings: QrSettingsPreferences
) : QrAppearancePreferences {

    override suspend fun useDynamicColors(): Boolean {
        return qrSettings.getQrDynamicColorsEnabled()
    }

    override suspend fun isSpoilerEnabled(): Boolean {
        return qrSettings.getQrSpoilerEnabled()
    }

    override suspend fun spoilerAnimationType(): QrAnimationType {
        return qrSettings.getQrSpoilerAnimationType()
    }
}
