package dev.alllexey.itmowidgets.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.rememberResultEventBus
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.designsystem.host.ItmoComposeHost
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The Navigation 3 shell (ADR 0020): the [surface] the session allows and, on the tabs, the [navigator]'s
 * `ShellBackStack` in one `NavDisplay`. Sheets and dialogs are overlay scenes above everything; the tab root and the
 * overlay screens are the shell's own layers ([ShellLayers]). Everything sits in [ItmoTheme] inside the host-level
 * composition locals every Fragment-hosted screen gets ([ItmoComposeHost]), plus the Navigation 3 result bus, so a
 * screen behaves here as in its Fragment host.
 */
@Composable
fun AppShell(
    navigator: Nav3AppNavigator,
    registry: EntryRegistry,
    surface: ShellSurface,
    onDemoSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ItmoComposeHost.locals {
        ItmoTheme {
            ShellContent(navigator, registry, surface, onDemoSignIn, modifier)
        }
    }
}

/**
 * [AppShell] without the theme and the host locals, for tests and captures that bring their own. Each tab and the
 * contextual stack (overlays, sheets, dialogs) has its own decorator set, computed on every composition: a hidden
 * tab's root stays in its own stack, so its decorators never see a pop and its saveable state and ViewModels
 * survive the switch (SP-14's multiple back stacks).
 */
@Composable
internal fun ShellContent(
    navigator: Nav3AppNavigator,
    registry: EntryRegistry,
    surface: ShellSurface,
    onDemoSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = navigator.state
    val tabRoots = AppTab.entries.associateWith { tab ->
        key(tab) { decoratedStack(listOf(AppRoutes.TabRoot(tab)), registry, navigator).single() }
    }
    val contextual = decoratedStack(state.overlays + state.floating, registry, navigator)
    val gate = decoratedStack(listOfNotNull(surface.gateRoute), registry, navigator)

    LaunchedEffect(surface) {
        if (surface == ShellSurface.Auth) navigator.dismissOverlays()
    }

    CompositionLocalProvider(LocalResultEventBus provides rememberResultEventBus()) {
        when (surface) {
            ShellSurface.Progress -> GateProgress()
            ShellSurface.Auth, ShellSurface.Onboarding -> gate.single().Content()
            is ShellSurface.Tabs -> ShellDisplay(
                tabRoot = tabRoots.getValue(state.tab),
                contextual = contextual,
                navigator = navigator,
                demoBanner = surface.demoBanner,
                onDemoSignIn = onDemoSignIn,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ShellDisplay(
    tabRoot: NavEntry<AppRoute>,
    contextual: List<NavEntry<AppRoute>>,
    navigator: Nav3AppNavigator,
    demoBanner: Boolean,
    onDemoSignIn: () -> Unit,
    modifier: Modifier,
) {
    val banner by rememberUpdatedState(demoBanner)
    val signIn by rememberUpdatedState(onDemoSignIn)
    val shellScenes = remember(navigator) {
        ShellSceneStrategy<AppRoute> { entries ->
            ShellLayers(entries.first(), entries.drop(1), navigator, banner, signIn)
        }
    }
    val sceneStrategies = remember(shellScenes) {
        listOf(DialogSceneStrategy<AppRoute>(), BottomSheetSceneStrategy(), shellScenes)
    }
    NavDisplay(
        entries = listOf(tabRoot) + contextual,
        modifier = modifier,
        sceneStrategies = sceneStrategies,
        onBack = { navigator.back() },
    )
}

/** One back stack decorated with its own saveable state, ViewModel stores and route arguments. */
@Composable
private fun decoratedStack(
    routes: List<AppRoute>,
    registry: EntryRegistry,
    navigator: Nav3AppNavigator,
): List<NavEntry<AppRoute>> {
    val entries = remember(registry, navigator) { EntryCache(registry, navigator) }.entries(routes)
    val decorators: List<NavEntryDecorator<AppRoute>> = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
        rememberRouteArgsDecorator(),
    )
    return rememberDecoratedNavEntries(entries, decorators)
}

/**
 * The undecorated entries of one stack, reused while their key stays, so scenes compare equal across compositions.
 * Equal routes in one stack (a profile opened again from a friend list) get distinct content keys by occurrence.
 */
private class EntryCache(private val registry: EntryRegistry, private val navigator: Nav3AppNavigator) {
    private var cached: Map<Any, NavEntry<AppRoute>> = emptyMap()

    fun entries(routes: List<AppRoute>): List<NavEntry<AppRoute>> {
        val seen = mutableMapOf<AppRoute, Int>()
        val entries = routes.map { route ->
            val occurrence = seen.merge(route, 1, Int::plus)!! - 1
            val contentKey = if (occurrence == 0) route.toString() else "$route#$occurrence"
            cached[contentKey] ?: registry.entry(route, contentKey, navigator)
        }
        cached = entries.associateBy { it.contentKey }
        return entries
    }
}

private val ShellSurface.gateRoute: AppRoute?
    get() = when (this) {
        ShellSurface.Auth -> AppRoutes.Auth
        ShellSurface.Onboarding -> AppRoutes.Onboarding
        ShellSurface.Progress, is ShellSurface.Tabs -> null
    }
