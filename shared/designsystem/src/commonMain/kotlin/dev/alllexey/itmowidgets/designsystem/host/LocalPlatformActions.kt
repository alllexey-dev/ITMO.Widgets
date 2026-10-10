package dev.alllexey.itmowidgets.designsystem.host

import androidx.compose.runtime.staticCompositionLocalOf
import dev.alllexey.itmowidgets.core.platform.PlatformActions

/**
 * The [PlatformActions] of the screen's host: on Android the Navigation 3 shell (`ShellContent`) provides them, on iOS
 * `screenController`. There is no default: a screen read outside a host fails at once instead of turning every link
 * and system page into a silent no-op; a test provides its own (`RecordingPlatformActions`).
 */
val LocalPlatformActions = staticCompositionLocalOf<PlatformActions> {
    error("LocalPlatformActions is not provided: the host must provide its PlatformActions")
}
