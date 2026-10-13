package dev.alllexey.itmowidgets.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRouteRegistration
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.OpenDecision
import dev.alllexey.itmowidgets.core.navigation.RouteKind
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import dev.alllexey.itmowidgets.core.navigation.appRouteSerializersModule
import kotlinx.serialization.json.Json

/**
 * The only mutator of the shell's [ShellBackStack]: entries get it as callbacks, the bar selects through it and Back
 * goes through it. Every command is the back stack's reducer applied to the observable [state], which
 * [rememberNav3AppNavigator] keeps in saved state, so the tab, the overlays, the sheets and the tab requests survive
 * recreation and process death.
 *
 * [guard] is asked before anything opens ([dev.alllexey.itmowidgets.core.navigation.ShellGate.check] with the
 * current session); a refusal in the demo session reaches [onRefusedInDemo]. Neither is saved: the host sets them on
 * every composition.
 */
@Stable
class Nav3AppNavigator(initial: ShellBackStack = ShellBackStack()) {

    var state: ShellBackStack by mutableStateOf(initial)
        private set

    var guard: (AppRoute) -> OpenDecision = { OpenDecision.OPEN }

    var onRefusedInDemo: () -> Unit = {}

    val tab: AppTab get() = state.tab

    /** Selecting or reselecting a tab closes every overlay, sheet and dialog. */
    fun select(tab: AppTab) {
        state = state.select(tab)
    }

    /**
     * Opens [route] in its layer. A second tap on what is already the top overlay opens nothing; a sheet or dialog
     * of a class or group already shown is refused by the back stack.
     */
    fun open(route: AppRoute): OpenDecision {
        val decision = guard(route)
        when (decision) {
            OpenDecision.OPEN -> if (!isTopOverlay(route)) state = state.open(route)
            OpenDecision.REFUSE_IN_DEMO -> onRefusedInDemo()
            OpenDecision.IGNORE -> Unit
        }
        return decision
    }

    /** Back: the top sheet or dialog, the top overlay, then the start tab; false when Back leaves the app. */
    fun back(): Boolean {
        val next = state.back() ?: return false
        state = next
        return true
    }

    /** Closes [route] where it is, for a sheet, dialog or screen that closes itself. */
    fun close(route: AppRoute) {
        state = state.close(route)
    }

    fun dismissOverlays() {
        state = state.dismissOverlays()
    }

    /** Runs an entry route: its tab, overlay, tab request and alert. */
    fun apply(route: EntryRoute) {
        state = state.apply(route)
    }

    /** The request waiting for [tab]'s root; the root calls [consume] once it handled it. */
    fun pendingRequest(tab: AppTab): TabRequest? = state.pendingRequest(tab)

    fun consume(tab: AppTab) {
        state = state.consume(tab)
    }

    private fun isTopOverlay(route: AppRoute): Boolean =
        route.kind == RouteKind.SCREEN && state.floating.isEmpty() && state.overlays.lastOrNull() == route

    companion object {
        /** Saves the whole [ShellBackStack] as JSON; [featureRoutes] are the feature modules' key registrations. */
        fun saver(vararg featureRoutes: AppRouteRegistration): Saver<Nav3AppNavigator, String> {
            val json = Json { serializersModule = appRouteSerializersModule(*featureRoutes) }
            return Saver(
                save = { json.encodeToString(ShellBackStack.serializer(), it.state) },
                restore = { Nav3AppNavigator(json.decodeFromString(ShellBackStack.serializer(), it)) },
            )
        }
    }
}

/** The shell's navigator, kept in saved state; [guard] and [onRefusedInDemo] follow the latest composition. */
@Composable
fun rememberNav3AppNavigator(
    vararg featureRoutes: AppRouteRegistration,
    guard: (AppRoute) -> OpenDecision = { OpenDecision.OPEN },
    onRefusedInDemo: () -> Unit = {},
): Nav3AppNavigator {
    val saver = remember { Nav3AppNavigator.saver(*featureRoutes) }
    val navigator = rememberSaveable(saver = saver) { Nav3AppNavigator() }
    SideEffect {
        navigator.guard = guard
        navigator.onRefusedInDemo = onRefusedInDemo
    }
    return navigator
}
