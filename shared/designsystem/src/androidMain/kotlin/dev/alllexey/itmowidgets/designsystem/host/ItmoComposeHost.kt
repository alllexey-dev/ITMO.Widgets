package dev.alllexey.itmowidgets.designsystem.host

import androidx.compose.runtime.Composable

/**
 * The one hook for host-level composition locals around every Android Compose host (the shell and the Activities
 * that set Compose content). `:app` may install it once at start; until then it provides nothing.
 * `LocalPlatformActions` is not one of them: the shell provides it.
 */
object ItmoComposeHost {

    /** Wraps every host's content; it must call `content` exactly once. */
    @Volatile
    var locals: @Composable (content: @Composable () -> Unit) -> Unit = { content -> content() }
        private set

    /** Replaces [locals]; call it before the first host sets its content. */
    fun install(locals: @Composable (content: @Composable () -> Unit) -> Unit) {
        this.locals = locals
    }
}
