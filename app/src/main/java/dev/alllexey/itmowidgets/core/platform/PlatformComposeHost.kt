package dev.alllexey.itmowidgets.core.platform

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.alllexey.itmowidgets.designsystem.host.ItmoComposeHost
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions

/** The app's host locals around every Fragment-hosted Compose screen: [AndroidPlatformActions] of its activity. */
object PlatformComposeHost {

    /** Fills `ItmoComposeHost`; the Application calls it once at start, before any Fragment creates its view. */
    fun install() = ItmoComposeHost.install { content ->
        val context = LocalContext.current
        val actions = remember(context) { AndroidPlatformActions(context) }
        CompositionLocalProvider(LocalPlatformActions provides actions, content = content)
    }
}
