package dev.alllexey.itmowidgets.app.shell

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.entries.shellEntries
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRouteRegistration
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.EntryRouteParser
import dev.alllexey.itmowidgets.core.navigation.OnboardingStatus
import dev.alllexey.itmowidgets.core.navigation.OpenDecision
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.RouteQueue
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellGate
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.navigation.appRouteSerializersModule
import dev.alllexey.itmowidgets.core.navigation.from
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.designsystem.components.navigation.NavigationBarTokens
import dev.alllexey.itmowidgets.designsystem.host.ItmoComposeHost
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingGate
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingGateViewModel
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateGateViewModel
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import java.util.WeakHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.androidx.viewmodel.ext.android.getViewModel

/**
 * `MainActivity`'s Navigation 3 body, which SH-1b9 switches it to. Created in the activity's `onCreate` after
 * `super.onCreate`, it does everything the legacy `MainActivity` does besides the Fragment shell:
 * - an entry intent goes through [EntryRouteParser] into a [RouteQueue] that is saved with the activity's state; the
 *   route runs once the tabs show, after sign-in and the first-run flow: its tab, overlay, request and alert, its
 *   [ActivityRoutes] start and `reportShortcutUsed`. A Recents replay runs nothing.
 * - [ShellGate] decides the surface from the session and the first-run flag, and guards every open; a refusal in the
 *   demo session says `error_demo_unavailable`, and the demo banner's sign-in signs out.
 * - one update check per process outside the demo (`AppUpdateGateViewModel`), its offer opened as [updateRoute] only
 *   while RESUMED; Google Play's flexible update offers the restart in a snackbar above the bar.
 * - edge to edge, and `adjustResize`, so `WindowInsets.ime` also works on API 26-29.
 *
 * Dependencies come from Koin; the Hilt-owned ones through `di/bridge/ShellBridge`. [updateRoute] is the key of the
 * update screen (L16's `AppUpdateArgs`, SH-1b8); without it the shell checks for nothing. [featureRoutes] are the
 * feature modules' key registrations, for saving the back stack and the pending route.
 */
class ShellHost(
    private val activity: ComponentActivity,
    private val registry: EntryRegistry = shellEntries(),
    private val updateRoute: ((AppUpdate) -> AppRoute)? = null,
    private val featureRoutes: List<AppRouteRegistration> = emptyList(),
) {
    private val koin = KoinStarter.ensureStarted(activity)
    private val sessions: SessionRepository = koin.get()
    private val installState: InstallStateWatcher = koin.get()
    private val onboardingGate: OnboardingGateViewModel = activity.getViewModel()

    // Still Hilt's in the legacy shell until L16's LA-2b moves it to Koin; built here from the bridged check.
    private val updateGate = activity.shellViewModel { AppUpdateGateViewModel(koin.get()) }
    private val json = Json { serializersModule = appRouteSerializersModule(*featureRoutes.toTypedArray()) }

    private val routes = RouteQueue()

    /** Bumped by every offered route, so the shell looks at the queue again. */
    private var routeOffers by mutableIntStateOf(0)
    private val snackbars = SnackbarHostState()
    private var restartOffer: Job? = null
    private var navigator: Nav3AppNavigator? = null
    private var surface: ShellSurface = ShellSurface.Progress

    init {
        activity.enableEdgeToEdge()
        @Suppress("DEPRECATION")
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // A recreated activity already accepted its intent: only the route still waiting comes back.
        val restored = activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)
        if (restored != null) restore(restored) else accept(activity.intent)
        activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY, ::saveState)
        activity.addOnNewIntentListener(::accept)
        activity.lifecycle.addObserver(HostLifecycle())
        activity.lifecycleScope.launch { sessions.initialize() }
        hosts[activity] = this
        activity.setContent { Content() }
    }

    /** What the shell shows now, read-only, for `ShellProbe`; read it on the main thread. */
    internal val snapshot: ShellSnapshot
        get() = ShellSnapshot(surface, navigator?.state ?: ShellBackStack())

    @Composable
    private fun Content() {
        val session by sessions.state.collectAsState()
        val gate by onboardingGate.uiState.collectAsState()
        val onboarding = gate.status
        val surface = ShellGate.surface(session, onboarding)
        val navigator = rememberNav3AppNavigator(
            *featureRoutes.toTypedArray(),
            guard = ::check,
            onRefusedInDemo = ::refuseInDemo,
        )
        SideEffect {
            this.navigator = navigator
            this.surface = surface
        }
        // Routes are taken once the tabs show; a route offered later re-runs this.
        LaunchedEffect(navigator, surface, routeOffers) {
            if (!ShellGate.ready(session, onboarding)) return@LaunchedEffect
            routes.take(ready = true) { true }?.let { run(it, navigator) }
            if (updateRoute != null && ShellGate.checksForUpdate(session, onboarding)) updateGate.checkForUpdate()
        }
        LaunchedEffect(navigator) {
            val route = updateRoute ?: return@LaunchedEffect
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updateGate.offers.collect { navigator.open(route(it)) }
            }
        }
        ItmoComposeHost.locals {
            ItmoTheme {
                Box(Modifier.fillMaxSize()) {
                    ShellContent(navigator, registry, surface, onDemoSignIn = ::signOut)
                    SnackbarHost(
                        snackbars,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            // Above the bar, as the legacy snackbar's anchor; the bar is at least this tall.
                            .padding(bottom = if (surface is ShellSurface.Tabs) NavigationBarTokens.MinHeight else 0.dp),
                    )
                }
            }
        }
    }

    private fun check(route: AppRoute): OpenDecision =
        ShellGate.check(route, sessions.state.value, onboardingGate.uiState.value.status)

    /** Runs a route whose tab the shell can select now; Activities stay above everything. */
    private fun run(route: EntryRoute, navigator: Nav3AppNavigator) {
        navigator.apply(route)
        route.activity?.let { activity.startActivity(ActivityRoutes.intent(activity, it)) }
        route.shortcutId?.let { ShortcutManagerCompat.reportShortcutUsed(activity, it) }
    }

    private fun accept(intent: Intent) {
        val route = EntryRouteParser.parse(
            intent.action,
            intent.getIntExtra(UserScreenArgs.ISU, 0),
            RecordbookSubjectArgs.from(intent.extras),
            launchedFromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0,
            link = intent.dataString,
        ) ?: return
        routes.offer(route)
        routeOffers++
    }

    private fun saveState(): Bundle = Bundle().apply {
        putString(PENDING_ROUTE, routes.pending?.let { json.encodeToString(EntryRoute.serializer(), it) })
    }

    private fun restore(state: Bundle) {
        val route = state.getString(PENDING_ROUTE) ?: return
        routes.offer(json.decodeFromString(EntryRoute.serializer(), route))
    }

    private fun refuseInDemo() {
        Toast.makeText(activity, R.string.error_demo_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun signOut() {
        activity.lifecycleScope.launch { sessions.signOut() }
    }

    /** Google Play downloaded an update: one snackbar at a time offers the restart. */
    private fun offerRestart() {
        if (restartOffer?.isActive == true) return
        restartOffer = activity.lifecycleScope.launch {
            val result = snackbars.showSnackbar(
                message = activity.getString(R.string.update_downloaded),
                actionLabel = activity.getString(R.string.update_restart),
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) installState.completeUpdate()
        }
    }

    private inner class HostLifecycle : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            installState.start(::offerRestart)
        }

        override fun onPause(owner: LifecycleOwner) {
            installState.stop()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            hosts.remove(activity)
        }
    }

    internal companion object {
        private const val STATE_KEY = "dev.alllexey.itmowidgets.app.shell.ShellHost"
        private const val PENDING_ROUTE = "pending_route"

        /** The live host of each activity, until it is destroyed; main thread only. */
        private val hosts = WeakHashMap<Activity, ShellHost>()

        /** The host [activity] runs, or null while it runs the legacy Fragment shell. */
        fun of(activity: Activity): ShellHost? = hosts[activity]
    }
}

/** The shell's surface and back stack at one moment. */
internal data class ShellSnapshot(val surface: ShellSurface, val backStack: ShellBackStack)

/** The shell's own entries: the alert of a malformed app link (route map section 7, L1). */
fun EntryRegistry.Builder.shellHostEntries() {
    entry<AppRoutes.LinkUnavailable> { key, navigator -> LinkUnavailableAlert(onDismiss = { navigator.close(key) }) }
}

/** `MainActivity`'s `MaterialAlertDialogBuilder` alert in the kit's dialog layout; Back and a tap outside close it. */
@Composable
private fun LinkUnavailableAlert(onDismiss: () -> Unit) {
    val title = stringResource(R.string.app_link_unavailable_title)
    val padding = ItmoTheme.spacing.section
    Surface(
        Modifier
            .sizeIn(minWidth = DialogMinWidth, maxWidth = DialogMaxWidth)
            .semantics { paneTitle = title },
        shape = ItmoTheme.shapes.extraLarge,
        color = ItmoTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(vertical = padding)) {
            Text(
                title,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = padding)
                    .semantics { heading() },
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.app_link_unavailable_text),
                Modifier.padding(top = ItmoTheme.spacing.group).padding(horizontal = padding),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyMedium,
            )
            Row(
                Modifier
                    .align(Alignment.End)
                    .padding(top = padding, start = padding, end = padding),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_got_it)) }
            }
        }
    }
}

private val OnboardingGate.status: OnboardingStatus
    get() = when (this) {
        OnboardingGate.Unknown -> OnboardingStatus.UNKNOWN
        OnboardingGate.Required -> OnboardingStatus.REQUIRED
        OnboardingGate.Passed -> OnboardingStatus.PASSED
    }

/** A ViewModel of the activity built by [create], never through Hilt's default factory. */
private inline fun <reified VM : ViewModel> ComponentActivity.shellViewModel(crossinline create: () -> VM): VM =
    ViewModelProvider(this, viewModelFactory { initializer { create() } })[VM::class.java]

/** material3's `DialogMinWidth` and `DialogMaxWidth`, as the kit's dialogs. */
private val DialogMinWidth = 280.dp
private val DialogMaxWidth = 560.dp
