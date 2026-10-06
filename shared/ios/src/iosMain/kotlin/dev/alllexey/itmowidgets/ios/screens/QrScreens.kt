package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.qr.ui.QrPassRoute
import platform.UIKit.UIViewController

/**
 * The QR pass above home (`AppRoutes.QrPass`), LH-3's route as Android hosts it. The Swift host fills the screen and
 * ignores the safe area, so the surface runs under the status bar and behind the tab bar while the content keeps
 * clear of both: the route draws its own top bar and no insets (DS-03), the host pads them here. [onBack] pops the
 * Swift stack; brightness is the Swift host's.
 */
fun qrPassViewController(onBack: () -> Unit): UIViewController = screenController {
    Box(
        Modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        QrPassRoute(onBack = onBack)
    }
}
