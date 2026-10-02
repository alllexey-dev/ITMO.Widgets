package dev.alllexey.itmowidgets.feature.settings.domain

/** Whether the app may ask the system to add its quick-settings tile (Android 13+). */
interface QuickSettingsTileAccess {
    fun canRequestAdd(): Boolean
}

/** The system's answer to a request to add the QR pass tile. */
enum class QrTileAddResult { ADDED, ALREADY_ADDED, NOT_ADDED, IN_PROGRESS, FAILED }
