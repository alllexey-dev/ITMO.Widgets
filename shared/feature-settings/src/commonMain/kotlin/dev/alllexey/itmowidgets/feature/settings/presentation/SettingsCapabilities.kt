package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities

/**
 * Every feature on, what Android offers: the default of the page providers that hide a row by capability, so code
 * that builds them by hand (screen tests, the debug fixtures) shows exactly what 2.2 showed. Koin passes the
 * platform's own [PlatformCapabilities].
 */
internal val EveryPlatformCapability = PlatformCapabilities(
    quickSettingsTile = true,
    backgroundWorkSettings = true,
    updateChannel = true,
    calendarExport = true,
    recordbook = true,
    marks = true,
    reviews = true,
    qrWidgetAnimation = true,
    qrCustomSpoiler = true,
)
