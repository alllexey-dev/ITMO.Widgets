package dev.alllexey.itmowidgets.designsystem.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

// Generators are cheap and kept, as UIKit recommends; the kit fires them on the main thread from input callbacks.
private val selection by lazy { UISelectionFeedbackGenerator() }
private val lightImpact by lazy { UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight) }
private val mediumImpact by lazy { UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium) }
private val notification by lazy { UINotificationFeedbackGenerator() }

internal actual fun performNativeHaptic(event: ItmoHapticEvent) {
    when (event) {
        ItmoHapticEvent.Selection -> selection.selectionChanged()
        ItmoHapticEvent.Toggle -> lightImpact.impactOccurred()
        ItmoHapticEvent.RefreshTrigger -> mediumImpact.impactOccurred()
        ItmoHapticEvent.Warning ->
            notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
        ItmoHapticEvent.Success ->
            notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        ItmoHapticEvent.Error ->
            notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
    }
}
