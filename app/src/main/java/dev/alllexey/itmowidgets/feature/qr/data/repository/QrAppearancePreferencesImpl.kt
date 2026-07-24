package dev.alllexey.itmowidgets.feature.qr.data.repository

import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import javax.inject.Inject

class QrAppearancePreferencesImpl @Inject constructor(
    private val settings: AppSettingsStorage
) : QrAppearancePreferences {

    override suspend fun useDynamicColors(): Boolean {
        return settings.getQrDynamicColorsEnabled()
    }
}
