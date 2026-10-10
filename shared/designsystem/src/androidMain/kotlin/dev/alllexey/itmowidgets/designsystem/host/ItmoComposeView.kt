package dev.alllexey.itmowidgets.designsystem.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The view a Fragment returns from `onCreateView` to show Compose content: [ItmoTheme] inside the host-level
 * composition locals of [ItmoComposeHost], disposed with the Fragment's view, not with its window.
 */
fun Fragment.itmoComposeView(content: @Composable () -> Unit): ComposeView = ComposeView(requireContext()).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        ItmoComposeHost.locals {
            ItmoTheme(content = content)
        }
    }
}

/**
 * The one hook for host-level composition locals around every Fragment-hosted screen. `:app` may install it once
 * at start; until then it provides nothing. `LocalPlatformActions` is not one of them: the shell provides it.
 */
object ItmoComposeHost {

    /** Wraps every [itmoComposeView]'s content; it must call `content` exactly once. */
    @Volatile
    var locals: @Composable (content: @Composable () -> Unit) -> Unit = { content -> content() }
        private set

    /** Replaces [locals]; call it before the first Fragment creates its view. */
    fun install(locals: @Composable (content: @Composable () -> Unit) -> Unit) {
        this.locals = locals
    }
}
