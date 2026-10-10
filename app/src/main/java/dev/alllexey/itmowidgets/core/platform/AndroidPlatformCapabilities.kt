package dev.alllexey.itmowidgets.core.platform

import android.os.Build

/** Android offers every feature 2.2 showed; the wallpaper's colours only from Android 12 (API 31). */
val AndroidPlatformCapabilities = PlatformCapabilities(
    quickSettingsTile = true,
    backgroundWorkSettings = true,
    updateChannel = true,
    calendarExport = true,
    recordbook = true,
    marks = true,
    reviews = true,
    qrWidgetAnimation = true,
    qrCustomSpoiler = true,
    wallpaperColors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
)
