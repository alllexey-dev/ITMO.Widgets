package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.platform.PlatformActions

/** Records each action in [actions] as `<action>:<argument>`; [handled] false acts as a device without a handler. */
class RecordingPlatformActions(var handled: Boolean = true) : PlatformActions {
    val actions = mutableListOf<String>()

    override fun shareText(title: String, text: String): Boolean = record("share:$title|$text")

    override fun openLink(url: String): Boolean = record("link:$url")

    override fun openMap(destination: MapDestination): Boolean = record("map:${destination.label}")

    override fun openAppSettings(): Boolean = record("appSettings")

    override fun openNotificationSettings(): Boolean = record("notificationSettings")

    private fun record(action: String): Boolean {
        actions += action
        return handled
    }
}
