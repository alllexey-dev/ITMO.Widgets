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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
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
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPinRequester
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
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
import dev.alllexey.itmowidgets.feature.onboarding.ui.OnboardingWidgetPreviews
import dev.alllexey.itmowidgets.feature.onboarding.ui.canStepBack
import dev.alllexey.itmowidgets.feature.onboarding.ui.handleOnboardingEvent
import dev.alllexey.itmowidgets.feature.onboarding.ui.onNotificationsEnabled
import dev.alllexey.itmowidgets.feature.onboarding.ui.onSpoilerCropped
import dev.alllexey.itmowidgets.feature.onboarding.ui.onWidgetPinnedProvider
import dev.alllexey.itmowidgets.feature.onboarding.ui.onboardingActions
import dev.alllexey.itmowidgets.feature.onboarding.ui.openOnboardingLink
import dev.alllexey.itmowidgets.feature.update.navigation.UpdateRoutes
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdateRoute
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction
import dev.alllexey.itmowidgets.feature.update.ui.toBundle
import dev.alllexey.itmowidgets.feature.web.ui.MyItmoBrowser
import dev.alllexey.itmowidgets.feature.web.ui.MyItmoWebContent
import dev.alllexey.itmowidgets.feature.web.ui.openExternalPage
import dev.alllexey.itmowidgets.feature.weblogin.data.BiometricDeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ConnectionsViewModel
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import dev.alllexey.itmowidgets.feature.weblogin.ui.ConnectionsRoute
import dev.alllexey.itmowidgets.feature.weblogin.ui.WebLoginSheetRoute
import dev.alllexey.itmowidgets.feature.weblogin.ui.scanWebLoginCode
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

/**
 * The account keys: sign-in and the first-run flow (the gate
 * surfaces), the «Подключить устройство» sheet and «Подключённые устройства», My ITMO in the browser, the update offer keyed by the feature's
 * [UpdateRoutes.AppUpdate], and the debug tools when [debugTools] (debug builds only, as the Me row that opens them).
 * The shell's guard refuses the web sign-in and My ITMO in the demo session before they open.
 */
internal fun EntryRegistry.Builder.accountEntries(debugTools: Boolean) {
    entry<AppRoutes.Auth> { _, _ -> AuthEntry() }
    entry<AppRoutes.Onboarding> { _, _ -> OnboardingEntry() }
    entry<AppRoutes.WebLogin> { key, navigator ->
        WebLoginEntry(
            key.code,
            onOpenConnections = { navigator.open(AppRoutes.Connections) },
            onClose = { navigator.close(key) },
        )
    }
    entry<AppRoutes.Connections> { key, navigator -> ConnectionsEntry(onBack = { navigator.close(key) }) }
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
 * social screens, the settings (the privacy page for the privacy row), the web sign-in sheet and, in debug builds,
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
    onOpenPrivacy = { navigator.open(AppRoutes.Settings(PRIVACY_PAGE)) },
    onOpenConnection = { navigator.open(AppRoutes.Settings(SERVICES_PAGE)) },
    // The settings root holds the notification and widget rows until their own pages exist.
    onOpenNotifications = { navigator.open(AppRoutes.Settings()) },
    onOpenWidgets = { navigator.open(AppRoutes.Settings()) },
    onOpenWebLogin = { navigator.open(AppRoutes.WebLogin()) },
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
 * The first-run flow: the real widget previews, the launcher pin (its receiver lives as long as this entry,
 * so a pin confirmed while the app is stopped still counts), the notification permission, the spoiler photo
 * picker and crop screen, links, and Back as a step back from the second step on. The shell leaves the flow on its
 * own once the first-run flag is set.
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
    val showSnackbar: (Int) -> Unit = { message ->
        scope.launch { snackbars.showSnackbar(resources.getString(message), duration = SnackbarDuration.Long) }
    }
    val notifications = rememberLauncherForActivityResult(RequestNotificationPermission()) { granted ->
        viewModel.onNotificationPermission(granted)
    }
    val pickSpoilerImage = rememberSpoilerImagePicker { result ->
        viewModel.onSpoilerCropped(result) { showSnackbar(R.string.settings_qr_custom_image_failed) }
    }
    val pinRequester = remember(context) { WidgetPinRequester(context) }
    DisposableEffect(pinRequester, viewModel) {
        pinRequester.start(viewModel::onWidgetPinnedProvider)
        viewModel.onPinSupportChanged(pinRequester.isSupported)
        onDispose { pinRequester.stop() }
    }
    val factory = remember(context) { KoinStarter.ensureStarted(context).get<WidgetPreviewFactory>() }
    val previews = remember(factory) { OnboardingWidgetPreviews(factory) }
    DisposableEffect(previews) {
        factory.preload(context, lifecycleOwner.lifecycleScope)
        onDispose { previews.close() }
    }
    LifecycleStartEffect(previews) { onStopOrDispose { previews.stop() } }
    LifecycleResumeEffect(viewModel) {
        viewModel.onNotificationsEnabled(context)
        onPauseOrDispose { }
    }
    BackHandler(enabled = state.canStepBack) { viewModel.back() }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                context.handleOnboardingEvent(event, pinRequester, notifications, showSnackbar)
            }
        }
    }
    val actions = remember(viewModel, pickSpoilerImage) {
        onboardingActions(
            viewModel,
            onPickSpoilerImage = pickSpoilerImage,
            onOpenLink = { url -> context.openOnboardingLink(url) { showSnackbar(R.string.link_open_failed) } },
        )
    }
    OnboardingContent(
        state = state,
        actions = actions,
        widgetPreview = { kind, modifier -> previews.Slot(kind, state, lifecycleOwner.lifecycleScope, modifier) },
        snackbars = snackbars,
    )
}

/**
 * The «Подключить устройство» sheet: Google's code scanner runs in Play services, the screen lock is `BiometricPrompt`
 * on this activity. A [link] from an App Link (a pairing link) is read once as a scan, not again after recreation.
 */
@Composable
private fun WebLoginEntry(link: String?, onOpenConnections: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // MainActivity is a FragmentActivity; a host without fragments (a test) has no screen lock to ask.
    val unlock = remember(activity) {
        (activity as? FragmentActivity)?.let { BiometricDeviceUnlock(it, UiTextResolver { text -> text.resolve() }) }
            ?: DeviceUnlock { DeviceUnlockResult.NOT_SET_UP }
    }
    val viewModel: WebLoginViewModel = entryViewModel()
    var linkRead by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(link) {
        if (link != null && !linkRead) {
            linkRead = true
            viewModel.onScanned(link)
        }
    }
    WebLoginSheetRoute(
        onScan = { scanWebLoginCode(context, viewModel) },
        onOpenConnections = onOpenConnections,
        onClose = onClose,
        unlock = unlock,
        viewModel = viewModel,
    )
}

/** «Подключённые устройства», opened from the sheet and from the settings. */
@Composable
private fun ConnectionsEntry(onBack: () -> Unit) {
    val viewModel: ConnectionsViewModel = entryViewModel()
    ConnectionsRoute(onBack = onBack, viewModel = viewModel)
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

/** The privacy page of the settings (`SettingsPage.PRIVACY`), as the Me tab opens it. */
private const val PRIVACY_PAGE = "PRIVACY"

/** `SettingsPage.SERVICES`: the connection to ITMO.Widgets, until "Подключение и данные" has its own page. */
private const val SERVICES_PAGE = "SERVICES"
