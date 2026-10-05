package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.core.location.MapDestination

/**
 * What a shared screen asks of the system and Compose Multiplatform does not offer. Android starts the matching
 * activity, iOS the matching system sheet or URL. Each call answers false when nothing on the device can handle it,
 * so the screen shows its own failure message.
 */
interface PlatformActions {

    /** The system share sheet for plain [text] (a link included); [title] heads the sheet. */
    fun shareText(title: String, text: String): Boolean

    /** An https [url] outside the app; t.me links open in Telegram when it is installed. False for any other scheme. */
    fun openLink(url: String): Boolean

    /** [destination] in the map app: any `geo:` handler on Android, Apple Maps on iOS. */
    fun openMap(destination: MapDestination): Boolean

    /** The app's page in the system settings. */
    fun openAppSettings(): Boolean

    /** The app's notification settings: the system switch and, on Android, the channels. */
    fun openNotificationSettings(): Boolean
}
