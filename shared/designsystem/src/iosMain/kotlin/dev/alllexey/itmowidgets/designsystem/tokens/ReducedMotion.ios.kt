package dev.alllexey.itmowidgets.designsystem.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

/**
 * The user's Reduce Motion setting (Settings, Accessibility, Motion), followed while the composition lives: UIKit
 * posts a notification when it changes, so a screen that stays open picks the change up.
 */
@Composable
actual fun rememberReducedMotion(): Boolean {
    var reduced by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> reduced = UIAccessibilityIsReduceMotionEnabled() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return reduced
}
