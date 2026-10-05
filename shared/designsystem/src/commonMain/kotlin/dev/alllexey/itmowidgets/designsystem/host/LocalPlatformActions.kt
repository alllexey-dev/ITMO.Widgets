package dev.alllexey.itmowidgets.designsystem.host

import androidx.compose.runtime.staticCompositionLocalOf
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.platform.PlatformActions

/**
 * The [PlatformActions] of the screen's host: Android sets it through `ItmoComposeHost`, the iOS host around its
 * Compose content. Previews and tests without a host get [NoPlatformActions].
 */
val LocalPlatformActions = staticCompositionLocalOf<PlatformActions> { NoPlatformActions }

/** Handles nothing: every action answers false, as on a device without a handler. */
object NoPlatformActions : PlatformActions {
    override fun shareText(title: String, text: String): Boolean = false
    override fun openLink(url: String): Boolean = false
    override fun openMap(destination: MapDestination): Boolean = false
    override fun openAppSettings(): Boolean = false
    override fun openNotificationSettings(): Boolean = false
}
