package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.ios.di.IosKoin
import platform.UIKit.UIViewController

/**
 * The one way a Compose screen reaches Swift: [content] in [ItmoTheme] inside the host-level composition locals, as
 * `itmoComposeView` gives a Fragment on Android. Each feature's IO card adds `screens/<Feature>Screens.kt` with
 * Swift-facing factories that call it; feature modules never build a `ComposeUIViewController` themselves.
 *
 * `koinViewModel()` inside [content] resolves into the `ViewModelStore` and saved state that Compose Multiplatform
 * gives each controller, not into a Swift-owned `ScreenViewModelStore`. Needs the started graph (`startKoinIos`).
 */
internal fun screenController(content: @Composable () -> Unit): UIViewController {
    val actions = IosKoin.koin().get<PlatformActions>()
    return ComposeUIViewController {
        CompositionLocalProvider(LocalPlatformActions provides actions) {
            ItmoTheme(content = content)
        }
    }
}
