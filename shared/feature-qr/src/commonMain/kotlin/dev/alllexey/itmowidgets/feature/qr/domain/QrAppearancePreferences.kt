package dev.alllexey.itmowidgets.feature.qr.domain

import dev.alllexey.itmowidgets.core.settings.QrAnimationType

interface QrAppearancePreferences {

    suspend fun useDynamicColors(): Boolean

    /** When disabled the widget shows the code straight away, without a spoiler. */
    suspend fun isSpoilerEnabled(): Boolean

    suspend fun spoilerAnimationType(): QrAnimationType
}
