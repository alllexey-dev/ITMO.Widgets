package dev.alllexey.itmowidgets.designsystem.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look the kit draws its components in (ADR 0023, amended 2026-10-06): [Material] on Android, [Ios] on iOS, where
 * the shared screens use iOS type, spacing, radii, colours and haptics. `ItmoTheme` picks it once; only the kit
 * branches on it, never feature code (Konsist, KN-02b).
 */
enum class ItmoPlatformStyle(
    /** The smallest touch target of an actionable element: Material's 48 dp, Apple's 44 pt (HIG, Accessibility). */
    val minTouchTarget: Dp,
) {
    Material(48.dp),
    Ios(44.dp),
}

/** [ItmoPlatformStyle.Material] on Android, [ItmoPlatformStyle.Ios] on iOS. */
expect fun defaultPlatformStyle(): ItmoPlatformStyle

internal val LocalItmoPlatformStyle = staticCompositionLocalOf { ItmoPlatformStyle.Material }
