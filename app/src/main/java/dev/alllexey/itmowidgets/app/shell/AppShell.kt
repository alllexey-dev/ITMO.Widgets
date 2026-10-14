package dev.alllexey.itmowidgets.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.rememberResultEventBus
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.platform.AndroidPlatformActions
import dev.alllexey.itmowidgets.designsystem.host.ItmoComposeHost
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The Navigation 3 shell: the [surface] the session allows and, on the tabs, the [navigator]'s `ShellBackStack` in one
 * `NavDisplay`, which animates every change and steps the entries' lifecycles; the shell draws the predictive Back
 * ([ShellBack]). Everything
 * sits in [ItmoTheme] inside the host-level composition locals ([ItmoComposeHost]), plus the Navigation 3 result bus
 * and the activity's [AndroidPlatformActions].
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
 * [AppShell] without the theme and the host locals, for tests and captures that bring their own. It provides
 * [LocalPlatformActions] itself, so no host can leave the entries' links and system pages on a no-op.
 *
 * Per-tab stacks as in the Navigation 3 `multiplestacks` recipe and Now in Android: each tab's stack (its root) is
 * decorated on its own, every composition, so a hidden tab's decorators never see a pop and its saveable state and
 * ViewModels survive the switch; the contextual stack (pushed screens, sheets, dialogs) has its own decorators. The
 * display gets the start tab's root, the selected tab's root above it and the contextual stack on top, so Back from
 * another tab's root pops to the start tab like any other screen.
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
        key(tab) { decoratedStack(state.tabStack(tab), registry, navigator).single() }
    }
    val contextual = decoratedStack(state.overlays + state.floating, registry, navigator)
    val gate = decoratedStack(listOfNotNull(surface.gateRoute), registry, navigator)

    LaunchedEffect(surface) {
        if (surface == ShellSurface.Auth) navigator.dismissOverlays()
    }

    val context = LocalContext.current
    val platform = remember(context) { AndroidPlatformActions(context) }

    CompositionLocalProvider(
        LocalResultEventBus provides rememberResultEventBus(),
        LocalPlatformActions provides platform,
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(shellInsets)) {
            when (surface) {
                ShellSurface.Progress -> GateProgress()
                ShellSurface.Auth, ShellSurface.Onboarding -> gate.single().Content()
                is ShellSurface.Tabs -> ShellDisplay(
                    entries = state.shownTabs.map(tabRoots::getValue) + contextual,
                    chrome = {
                        TabChrome(
                            selected = state.tab,
                            covered = state.coversBar,
                            demoBanner = surface.demoBanner,
                            onSelect = navigator::select,
                            onDemoSignIn = onDemoSignIn,
                        )
                    },
                    onBack = { navigator.back() },
                    modifier = modifier,
                )
            }
        }
    }
}

/**
 * What the shell pads around every surface, as the legacy `MainActivity` padded its root: the status bar on top, the
 * system bars and a display cutout at the sides. Entries draw no window insets of their own there (`AppTopBar`). The
 * bottom stays edge to edge: the bar pads the navigation bar, a gate or pushed screen its own end. Sheets and dialogs
 * are windows of their own with their own insets.
 */
internal val shellInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/**
 * The [chrome] (the demo banner and the bar) at the bottom and the `NavDisplay` above it, laid out as M3's `Scaffold`
 * does: the chrome is measured first and its height reaches the tab roots ([LocalTabChromeHeight]), which end above
 * it. The chrome is drawn under the display, so a pushed screen covers it, slides over it and uncovers it in the
 * predictive Back like any screen; the tab roots draw nothing over it.
 */
@Composable
private fun ShellDisplay(
    entries: List<NavEntry<AppRoute>>,
    chrome: @Composable () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val sceneStrategies = remember { listOf(DialogSceneStrategy<AppRoute>(), BottomSheetSceneStrategy()) }
    SubcomposeLayout(modifier.fillMaxSize()) { constraints ->
        val chromePlaceables = subcompose(ShellSlot.CHROME, chrome)
            .map { it.measure(constraints.copy(minHeight = 0)) }
        val chromeHeight = chromePlaceables.maxOfOrNull { it.height } ?: 0
        val displayPlaceables = subcompose(ShellSlot.DISPLAY) {
            CompositionLocalProvider(LocalTabChromeHeight provides chromeHeight.toDp()) {
                ShellNavDisplay(entries, sceneStrategies, onBack)
            }
        }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            chromePlaceables.forEach { it.place(0, constraints.maxHeight - it.height) }
            displayPlaceables.forEach { it.place(0, 0) }
        }
    }
}

private enum class ShellSlot { CHROME, DISPLAY }

/** `NavDisplay` with the shell's transitions and the shell's own Back ([rememberShellBack]). */
@Composable
private fun ShellNavDisplay(
    entries: List<NavEntry<AppRoute>>,
    sceneStrategies: List<SceneStrategy<AppRoute>>,
    onBack: () -> Unit,
) {
    val sceneState = rememberSceneState(entries, sceneStrategies, onBack = onBack)
    val back = rememberShellBack(sceneState, onBack)
    CompositionLocalProvider(LocalShellBack provides back.back) {
        NavDisplay(
            sceneState = sceneState,
            navigationEventState = back.displayState,
            transitionSpec = ShellMotion.push,
            popTransitionSpec = ShellMotion.pop,
            predictivePopTransitionSpec = ShellMotion.predictivePop,
        )
    }
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
 * The undecorated entries of one stack in their shell layer ([inShellLayer]), reused while their key stays, so scenes
 * compare equal across compositions.
 * Equal routes in one stack (a profile opened again from a friend list) get distinct content keys by occurrence.
 */
private class EntryCache(private val registry: EntryRegistry, private val navigator: Nav3AppNavigator) {
    private var cached: Map<Any, NavEntry<AppRoute>> = emptyMap()

    fun entries(routes: List<AppRoute>): List<NavEntry<AppRoute>> {
        val seen = mutableMapOf<AppRoute, Int>()
        val entries = routes.map { route ->
            val occurrence = seen.merge(route, 1, Int::plus)!! - 1
            val contentKey = if (occurrence == 0) route.toString() else "$route#$occurrence"
            cached[contentKey] ?: registry.entry(route, contentKey, navigator).inShellLayer(route.kind)
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
