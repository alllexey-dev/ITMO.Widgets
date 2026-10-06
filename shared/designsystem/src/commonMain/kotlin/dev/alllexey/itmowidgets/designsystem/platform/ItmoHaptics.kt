package dev.alllexey.itmowidgets.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** What happened, in the terms of Apple's HIG (Playing haptics); a kit component names the event, not the motor. */
internal enum class ItmoHapticEvent {
    /** The selection moved: a segment, a picker value. */
    Selection,

    /** A switch or a checkbox changed. */
    Toggle,

    /** A pull went past the refresh threshold. */
    RefreshTrigger,
    Success,
    Warning,
    Error,
}

/** The kit's haptics: a kit component names the event, [rememberItmoHaptics] picks who plays it. */
internal fun interface ItmoHaptics {
    fun perform(event: ItmoHapticEvent)
}

/**
 * The platform's haptics. Under [ItmoPlatformStyle.Material] they do nothing, so Android keeps 2.2's behaviour. Under
 * [ItmoPlatformStyle.Ios] [ItmoHapticEvent.Success] and [ItmoHapticEvent.Error] go through [LocalHapticFeedback],
 * whose iOS implementation in CMP 1.12.1 plays `Confirm` and `Reject` on `UINotificationFeedbackGenerator` (success,
 * error); CMP has no warning and does not document its impact and selection mapping, so the other events drive
 * UIKit's generators in the iosMain actual ([performNativeHaptic]). Only kit components fire them, never features.
 */
@Stable
internal class PlatformHaptics(private val style: ItmoPlatformStyle, private val feedback: HapticFeedback) :
    ItmoHaptics {
    override fun perform(event: ItmoHapticEvent) {
        if (style == ItmoPlatformStyle.Material) return
        when (event) {
            ItmoHapticEvent.Success -> feedback.performHapticFeedback(HapticFeedbackType.Confirm)
            ItmoHapticEvent.Error -> feedback.performHapticFeedback(HapticFeedbackType.Reject)
            else -> performNativeHaptic(event)
        }
    }
}

/** A replacement for [PlatformHaptics] that kit tests provide to record which events a component fires. */
internal val LocalItmoHaptics = staticCompositionLocalOf<ItmoHaptics?> { null }

/** The haptics of the current theme's platform style, or the ones a test provides through [LocalItmoHaptics]. */
@Composable
internal fun rememberItmoHaptics(): ItmoHaptics {
    val style = LocalItmoPlatformStyle.current
    val feedback = LocalHapticFeedback.current
    val platform = remember(style, feedback) { PlatformHaptics(style, feedback) }
    return LocalItmoHaptics.current ?: platform
}

/**
 * Plays [event] on the platform's own generator: iOS `UISelectionFeedbackGenerator` (selection),
 * `UIImpactFeedbackGenerator` (toggle light, refresh medium) and `UINotificationFeedbackGenerator` (warning).
 * Nothing on Android, where only previews and tests draw the iOS style.
 */
internal expect fun performNativeHaptic(event: ItmoHapticEvent)
