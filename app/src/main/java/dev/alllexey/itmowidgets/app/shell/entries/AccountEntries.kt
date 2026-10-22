package dev.alllexey.itmowidgets.app.shell.entries

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.ActivityRoutes
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.text.UiTextResolver
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.ui.permission.RequestNotificationPermission
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropResult
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthViewModel
import dev.alllexey.itmowidgets.feature.auth.ui.AuthRoute
import dev.alllexey.itmowidgets.feature.auth.ui.confirmDemoEntry
import dev.alllexey.itmowidgets.feature.debug.presentation.DebugToolsViewModel
import dev.alllexey.itmowidgets.feature.debug.ui.DebugToolsScreen
import dev.alllexey.itmowidgets.feature.debug.ui.debugToolsActions
import dev.alllexey.itmowidgets.feature.debug.ui.handleDebugToolsEvent
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import dev.alllexey.itmowidgets.feature.me.ui.MeActions
import dev.alllexey.itmowidgets.feature.me.ui.MeHost
import dev.alllexey.itmowidgets.feature.me.ui.MeRoute
import dev.alllexey.itmowidgets.feature.me.ui.openProjectLink
import dev.alllexey.itmowidgets.feature.me.ui.shareOwnProfile
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import dev.alllexey.itmowidgets.feature.onboarding.ui.OnboardingContent
import dev.alllexey.itmowidgets.feature.onboarding.ui.canStepBack
import dev.alllexey.itmowidgets.feature.onboarding.ui.handleOnboardingEvent
import dev.alllexey.itmowidgets.feature.onboarding.ui.onNotificationsEnabled
import dev.alllexey.itmowidgets.feature.onboarding.ui.onboardingActions
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.ui.CALENDAR_PERMISSIONS
import dev.alllexey.itmowidgets.feature.update.navigation.UpdateRoutes
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdateRoute
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction
import dev.alllexey.itmowidgets.feature.update.ui.toBundle
import dev.alllexey.itmowidgets.feature.web.ui.MyItmoBrowser
import dev.alllexey.itmowidgets.feature.web.ui.MyItmoWebContent
import dev.alllexey.itmowidgets.feature.web.ui.openExternalPage
import dev.alllexey.itmowidgets.feature.connections.data.BiometricDeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.connections.presentation.ConnectionViewModel
import dev.alllexey.itmowidgets.feature.connections.presentation.DevicesViewModel
import dev.alllexey.itmowidgets.feature.connections.presentation.PairingViewModel
import dev.alllexey.itmowidgets.feature.connections.ui.ConnectionRoute
import dev.alllexey.itmowidgets.feature.connections.ui.DevicesRoute
import dev.alllexey.itmowidgets.feature.connections.ui.PairingSheetRoute
import dev.alllexey.itmowidgets.feature.connections.ui.scanPairingLink
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

/**
 * The account keys: sign-in and the first-run flow (the gate
 * surfaces), «Устройства», a client's screen and the «Подключить устройство» sheet, My ITMO in the browser, the update offer keyed by the feature's
 * [UpdateRoutes.AppUpdate], and the debug tools when [debugTools] (debug builds only, as the Me row that opens them).
 * The shell's guard refuses pairing, «Устройства» and My ITMO in the demo session before they open.
 */
internal fun EntryRegistry.Builder.accountEntries(debugTools: Boolean) {
    entry<AppRoutes.Auth> { _, _ -> AuthEntry() }
    entry<AppRoutes.Onboarding> { _, _ -> OnboardingEntry() }
    entry<AppRoutes.Pairing> { key, navigator -> PairingEntry(key.link, onClose = { navigator.close(key) }) }
    entry<AppRoutes.Devices> { key, navigator ->
        val viewModel: DevicesViewModel = entryViewModel()
        DevicesRoute(
            viewModel = viewModel,
            onBack = { navigator.close(key) },
            onConnect = { navigator.open(AppRoutes.Pairing()) },
            onEnableServer = { navigator.open(AppRoutes.Settings(SERVICES_PAGE)) },
            onOpenClient = { navigator.open(AppRoutes.Connection(it)) },
        )
    }
    entry<AppRoutes.Connection> { key, navigator -> ConnectionEntry(key.id, navigator, onBack = { navigator.close(key) }) }
    entry<AppRoutes.MyItmoWeb> { key, navigator -> MyItmoWebEntry(onClose = { navigator.close(key) }) }
    entry<UpdateRoutes.AppUpdate>(args = { it.args.toBundle() }) { key, navigator ->
        AppUpdateEntry(onClose = { navigator.close(key) })
    }
    if (debugTools) {
        entry<AppRoutes.DebugTools> { key, navigator -> DebugToolsEntry(onBack = { navigator.close(key) }) }
    }
}

/**
 * The Me tab's root: `MeRoute` with its ViewModel in the root's own store. Its rows open the
 * social screens, the settings (the privacy page for the privacy row), «Устройства» and, in debug builds,
 * the debug tools through [navigator]; sharing and the project pages go through the Android helpers of `MeHost.kt`.
 */
@Composable
internal fun MeTabRoot(navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val viewModel: MeViewModel = entryViewModel()
    val actions = remember(navigator, context) { meActions(navigator, context) }
    MeRoute(actions, showDebugTools = BuildConfig.DEBUG && !MeHost.releaseLook, viewModel = viewModel)
}

private fun meActions(navigator: Nav3AppNavigator, context: Context) = MeActions(
    onOpenFriends = { navigator.open(AppRoutes.Friends) },
    onOpenConnection = { navigator.open(AppRoutes.Settings(SERVICES_PAGE)) },
    onOpenDevices = { navigator.open(AppRoutes.Devices) },
    onOpenWidgets = { navigator.open(AppRoutes.Settings(SettingsPage.WIDGETS.name)) },
    onOpenMap = { navigator.open(AppRoutes.CampusMap()) },
    onOpenMyItmo = { navigator.open(AppRoutes.MyItmoWeb) },
    onOpenSettings = { navigator.open(AppRoutes.Settings()) },
    onOpenDebugTools = { navigator.open(AppRoutes.DebugTools) },
    onShareProfile = { name, isu ->
        context.shareOwnProfile(KoinStarter.ensureStarted(context).get<ShareLinkFactory>(), name, isu)
    },
    onOpenProjectLink = { link -> context.openProjectLink(link) },
)

/** Sign-in: ITMO ID in `LoginActivity` for a result; five taps on the logo enter the demo. */
@Composable
private fun AuthEntry() {
    val context = LocalContext.current
    val view = LocalView.current
    val viewModel: AuthViewModel = entryViewModel()
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) viewModel.clearError()
    }
    AuthRoute(
        onSignInWithItmoId = { login.launch(ActivityRoutes.login(context)) },
        onDemoStarted = { message -> confirmDemoEntry(context, view, message) },
        viewModel = viewModel,
    )
}

/**
 * The first-run flow: the notification permission, the calendar permission (granted, the sync turns on), the
 * consent sheet in this entry's saved state, and Back as a step back from the second step on. The shell leaves the
 * flow on its own once the first-run flag is set.
 */
@Composable
private fun OnboardingEntry() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: OnboardingViewModel = entryViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var consent by rememberSaveable { mutableStateOf(false) }
    val showSnackbar: (Int) -> Unit = { message ->
        scope.launch { snackbars.showSnackbar(resources.getString(message), duration = SnackbarDuration.Long) }
    }
    val notifications = rememberLauncherForActivityResult(RequestNotificationPermission()) { granted ->
        viewModel.onNotificationPermission(granted)
    }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.isNotEmpty() && results.values.all { it }) viewModel.onCalendarAccessGranted()
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onNotificationsEnabled(context)
        onPauseOrDispose { }
    }
    BackHandler(enabled = state.canStepBack) { viewModel.back() }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                context.handleOnboardingEvent(
                    event,
                    notifications,
                    askCalendar = { calendar.launch(CALENDAR_PERMISSIONS) },
                    showConsent = { consent = true },
                    showSnackbar = showSnackbar,
                )
            }
        }
    }
    val actions = remember(viewModel) { onboardingActions(viewModel, onShowConsent = { consent = it }) }
    OnboardingContent(state = state, actions = actions, consentShown = consent, snackbars = snackbars)
}

/**
 * «Подключить устройство»: Google's code scanner runs in Play services, the screen lock is `BiometricPrompt` on this
 * activity. A [link] from an App Link or a client's renewal is read once as a scan, not again after recreation.
 */
@Composable
private fun PairingEntry(link: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val unlock = rememberDeviceUnlock()
    val viewModel: PairingViewModel = entryViewModel()
    var linkRead by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(link) {
        if (link != null && !linkRead) {
            linkRead = true
            viewModel.onScanned(link)
        }
    }
    PairingSheetRoute(
        viewModel = viewModel,
        unlock = unlock,
        onScan = { scanPairingLink(context, viewModel::onScanned, viewModel::onScannerUnavailable) },
        onClose = onClose,
    )
}

/** A connected client; its renewal scans the client's QR and continues in the pairing sheet. */
@Composable
private fun ConnectionEntry(id: String, navigator: Nav3AppNavigator, onBack: () -> Unit) {
    val context = LocalContext.current
    val unlock = rememberDeviceUnlock()
    val viewModel: ConnectionViewModel = viewModel {
        KoinStarter.ensureStarted(context).get<ConnectionViewModel> { parametersOf(id) }
    }
    ConnectionRoute(
        viewModel = viewModel,
        unlock = unlock,
        onBack = onBack,
        onScan = { scanPairingLink(context, { navigator.open(AppRoutes.Pairing(link = it)) }, {}) },
    )
}

/** The screen lock on this activity; MainActivity is a FragmentActivity, a host without fragments (a test) has none. */
@Composable
private fun rememberDeviceUnlock(): DeviceUnlock {
    val activity = LocalActivity.current
    return remember(activity) {
        (activity as? FragmentActivity)?.let { BiometricDeviceUnlock(it, UiTextResolver { text -> text.resolve() }) }
            ?: DeviceUnlock { DeviceUnlockResult.NOT_SET_UP }
    }
}

/**
 * My ITMO in the browser: one [MyItmoBrowser] whose history, page and failure survive recreation in this
 * entry's saved state. Back goes back in the browser's history first; without history the shell's overlay handler
 * takes it, so the predictive gesture previews closing the screen.
 */
@Composable
private fun MyItmoWebEntry(onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    val openExternal: (String) -> Unit = remember(context, resources, scope, snackbars) {
        { url ->
            openExternalPage(context, url) {
                val message = resources.getString(R.string.link_open_failed)
                scope.launch { snackbars.showSnackbar(message, duration = SnackbarDuration.Long) }
            }
        }
    }
    val saver = remember(openExternal) {
        Saver<MyItmoBrowser, Bundle>(
            save = { it.save() },
            restore = { saved -> MyItmoBrowser(openExternal).apply { restore(saved) } },
        )
    }
    val browser = rememberSaveable(saver = saver) { MyItmoBrowser(openExternal).apply { restore(null) } }
    BackHandler(enabled = browser.canGoBack) { if (!browser.goBack()) onClose() }
    LifecycleResumeEffect(browser) {
        browser.onResume()
        onPauseOrDispose { browser.onPause() }
    }
    MyItmoWebContent(browser, onClose, snackbars)
}

/** The update offer: its update button runs the distribution's `UpdateAction` on this activity. */
@Composable
private fun AppUpdateEntry(onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val updateAction = remember(context) { KoinStarter.ensureStarted(context).get<UpdateAction>() }
    AppUpdateRoute(
        onUpdate = { unsupported, onFailed ->
            if (activity != null) updateAction.start(activity, unsupported, onFailed) else onFailed()
        },
        onClose = onClose,
    )
}

/** The debug tools, only ever registered in debug builds; overrides recreate the activity. */
@Composable
private fun DebugToolsEntry(onBack: () -> Unit) {
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: DebugToolsViewModel = entryViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event -> activity?.handleDebugToolsEvent(event) }
        }
    }
    val actions = remember(viewModel, onBack) { debugToolsActions(viewModel, onBack) }
    DebugToolsScreen(state, actions)
}

/**
 * The system photo picker, then the square crop screen (`SpoilerCropActivity`); a device without a picker answers
 * [SpoilerCropResult.Failed]. The first-run flow and the QR widget's
 * settings page pick with it.
 */
@Composable
internal fun rememberSpoilerImagePicker(onResult: (SpoilerCropResult) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val crop = rememberLauncherForActivityResult(ActivityRoutes.spoilerCrop()) { currentOnResult(it) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) crop.launch(uri)
    }
    return remember(pick) {
        {
            try {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (_: ActivityNotFoundException) {
                currentOnResult(SpoilerCropResult.Failed)
            }
        }
    }
}

/**
 * Koin's definition of [VM] in this entry's own store, with the entry's `SavedStateHandle` (its arguments, seeded by
 * the shell) as Koin's `koinViewModel()` passes it.
 */
@Composable
private inline fun <reified VM : ViewModel> entryViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}

/** `SettingsPage.SERVICES`: «Сервер ITMO.Widgets». */
private const val SERVICES_PAGE = "SERVICES"
