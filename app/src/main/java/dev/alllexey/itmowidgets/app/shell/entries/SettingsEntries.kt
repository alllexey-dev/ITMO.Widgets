package dev.alllexey.itmowidgets.app.shell.entries

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
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
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.SettingsScreenArgs
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.permission.RequestNotificationPermission
import dev.alllexey.itmowidgets.core.ui.permission.openAppNotificationSettings
import dev.alllexey.itmowidgets.core.ui.permission.requestNotifications
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import dev.alllexey.itmowidgets.feature.settings.ui.CALENDAR_PERMISSIONS
import dev.alllexey.itmowidgets.feature.settings.ui.CalendarAccessAnswer
import dev.alllexey.itmowidgets.feature.settings.ui.CalendarAccessStep
import dev.alllexey.itmowidgets.feature.settings.ui.ICS_DATES_TAG
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsActions
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsDialog
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsDialogState
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsScreen
import dev.alllexey.itmowidgets.feature.settings.ui.calendarAccessAnswer
import dev.alllexey.itmowidgets.feature.settings.ui.calendarAccessStep
import dev.alllexey.itmowidgets.feature.settings.ui.canOpenIcs
import dev.alllexey.itmowidgets.feature.settings.ui.copyDiagnosticsJournal
import dev.alllexey.itmowidgets.feature.settings.ui.diagnostics.DiagnosticsActions
import dev.alllexey.itmowidgets.feature.settings.ui.diagnostics.DiagnosticsScreen
import dev.alllexey.itmowidgets.feature.settings.ui.ics.IcsExportActions
import dev.alllexey.itmowidgets.feature.settings.ui.ics.IcsExportSheetContent
import dev.alllexey.itmowidgets.feature.settings.ui.listenToIcsDatePicker
import dev.alllexey.itmowidgets.feature.settings.ui.onCustomSpoilerCropped
import dev.alllexey.itmowidgets.feature.settings.ui.onSettingsNotificationPermission
import dev.alllexey.itmowidgets.feature.settings.ui.openBackgroundWorkSettings
import dev.alllexey.itmowidgets.feature.settings.ui.openIcs
import dev.alllexey.itmowidgets.feature.settings.ui.requestAddQrTile
import dev.alllexey.itmowidgets.feature.settings.ui.shareIcs
import dev.alllexey.itmowidgets.feature.settings.ui.showCalendarAccessDenied
import dev.alllexey.itmowidgets.feature.settings.ui.showCustomSpoilerEvent
import dev.alllexey.itmowidgets.feature.settings.ui.showIcsDatePicker
import dev.alllexey.itmowidgets.feature.settings.ui.showWidgetsRefreshStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

/**
 * The settings keys (route map rows 10-12 and S6): every settings page under its `SettingsScreenArgs.PAGE`, the error
 * journal and the `.ics` sheet. A page's rows open the next page, the journal and the sheet through the navigator;
 * the permission, picker and system-page launchers are the ones `SettingsFragment` uses, through the same helpers.
 */
internal fun EntryRegistry.Builder.settingsEntries() {
    entry<AppRoutes.Settings>(args = { bundleOf(SettingsScreenArgs.PAGE to it.page) }) { key, navigator ->
        SettingsEntry(key, navigator)
    }
    entry<AppRoutes.Diagnostics> { key, navigator -> DiagnosticsEntry(onBack = { navigator.close(key) }) }
    entry<AppRoutes.IcsExport> { _, _ -> IcsExportEntry() }
}

/**
 * One settings page (row 10), hosted as `SettingsFragment` hosts it: the page's widget preview, the shown dialog and
 * a pending calendar request in this entry's saved state, the row states refreshed on every resume, and on the QR
 * widget page the custom spoiler image with its own ViewModel. A sub-page opens as another [AppRoutes.Settings].
 */
@Composable
private fun SettingsEntry(key: AppRoutes.Settings, navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val platform = LocalPlatformActions.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SettingsViewModel = entryViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val page = remember(viewModel) { viewModel.uiState.value.page }
    // Only the QR widget page reads the spoiler image, as only it touches the Fragment's lazy ViewModel.
    val spoiler: CustomSpoilerViewModel? = if (page == SettingsPage.QR_WIDGET) entryViewModel() else null
    val dialogs = rememberSaveable(saver = DialogsSaver) { SettingsDialogState() }
    var pendingCalendarAccess by rememberSaveable { mutableStateOf(false) }
    val preview = rememberWidgetPreview(context)

    val notifications = rememberLauncherForActivityResult(RequestNotificationPermission()) { granted ->
        activity?.onSettingsNotificationPermission(granted, viewModel)
    }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (!pendingCalendarAccess || activity == null) return@rememberLauncherForActivityResult
        pendingCalendarAccess = false
        when (activity.calendarAccessAnswer(results)) {
            CalendarAccessAnswer.GRANTED -> viewModel.onCalendarAccessGranted()
            CalendarAccessAnswer.LOCKED -> dialogs.show(SettingsDialog.CalendarAccess(locked = true))
            CalendarAccessAnswer.DENIED -> view.showCalendarAccessDenied()
        }
    }
    val askCalendarAccess = {
        pendingCalendarAccess = true
        calendar.launch(CALENDAR_PERMISSIONS)
    }
    val pickSpoilerImage = rememberSpoilerImagePicker { result ->
        spoiler?.onCustomSpoilerCropped(result) {
            snackbar(view, R.string.settings_qr_custom_image_failed, Snackbar.LENGTH_LONG)
        }
    }

    LifecycleResumeEffect(viewModel) {
        // Back from a system page, the rows follow what Android allows now.
        viewModel.onNotificationPermissionChanged(NotificationManagerCompat.from(context).areNotificationsEnabled())
        viewModel.onBackgroundWorkChanged()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        if (page == SettingsPage.ROOT) preview.factory.preload(context, lifecycleOwner.lifecycleScope)
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    SettingsEvent.OpenNotificationSettings -> context.openAppNotificationSettings()
                    SettingsEvent.RequestNotificationPermission -> context.requestNotifications(notifications)
                    SettingsEvent.ChooseCustomSpoiler -> if (spoiler?.uiState?.value?.busy == false) pickSpoilerImage()
                    SettingsEvent.ResetCustomSpoiler -> spoiler?.resetImage()
                    SettingsEvent.OpenDiagnostics -> navigator.open(AppRoutes.Diagnostics)
                    // The root gate already switched to the flow; the overlay just has to leave.
                    SettingsEvent.CloseOverlays -> navigator.dismissOverlays()
                    SettingsEvent.OpenBackgroundWorkSettings -> activity?.openBackgroundWorkSettings()
                    SettingsEvent.ShowBackgroundWorkHint -> dialogs.show(SettingsDialog.BackgroundWorkHint)
                    SettingsEvent.RequestQrTile -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        activity?.requestAddQrTile(viewModel::onQrTileResult)
                    }
                    SettingsEvent.RequestCalendarAccess -> when (activity?.calendarAccessStep()) {
                        CalendarAccessStep.GRANTED -> viewModel.onCalendarAccessGranted()
                        CalendarAccessStep.ASK -> askCalendarAccess()
                        CalendarAccessStep.EXPLAIN -> dialogs.show(SettingsDialog.CalendarAccess(locked = false))
                        null -> Unit
                    }
                    SettingsEvent.OpenIcsExport -> navigator.open(AppRoutes.IcsExport)
                    else -> showSettingsMessage(event, context, view, platform)
                }
            }
        }
    }
    if (spoiler != null) CustomSpoilerEffects(spoiler, viewModel, preview, view)

    val actions = remember(viewModel, navigator, key, activity, platform) {
        SettingsActions(
            onBack = { navigator.close(key) },
            onToggle = { id, checked -> viewModel.onToggleChanged(id, checked) },
            onChoice = { id, optionKey -> viewModel.onChoiceChanged(id, optionKey) },
            onNavigate = { next -> navigator.open(AppRoutes.Settings(next.name)) },
            onAction = { id -> viewModel.onAction(id) },
            onAllowBackgroundWork = { activity?.openBackgroundWorkSettings() },
            onAllowCalendarAccess = askCalendarAccess,
            onOpenAppSettings = { platform.openAppSettings() },
        )
    }
    SettingsScreen(
        state = state,
        actions = actions,
        widgetPreview = { settings -> preview.Slot(settings) },
        dialogs = dialogs,
    )
}

/** The snackbars and the site pages of [event]; [SettingsEntry] handles the rest of [SettingsEvent]. */
private fun showSettingsMessage(event: SettingsEvent, context: Context, view: View, platform: PlatformActions) {
    when (event) {
        SettingsEvent.WidgetsRefreshStarted -> view.showWidgetsRefreshStarted()
        is SettingsEvent.OpenWebPage -> if (!platform.openLink(BuildConfig.WIDGETS_BASE_URL + event.path)) {
            snackbar(view, R.string.link_open_failed, Snackbar.LENGTH_SHORT)
        }
        // A switch always shows the state, so one the action did not change has nothing to undo.
        is SettingsEvent.ShowMessage ->
            Snackbar.make(view, event.text.resolve(context), Snackbar.LENGTH_SHORT).show()
        is SettingsEvent.ShowError -> snackbar(view, event.error.messageRes(), Snackbar.LENGTH_LONG)
        else -> Unit
    }
}

/**
 * The QR widget page's custom image: its state drives the image rows, and a saved or reset image redraws the preview
 * and says so, as `SettingsFragment.observeCustomSpoiler` does while the page is started.
 */
@Composable
private fun CustomSpoilerEffects(
    spoiler: CustomSpoilerViewModel,
    viewModel: SettingsViewModel,
    preview: SettingsWidgetPreview,
    view: View,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(spoiler, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                spoiler.uiState.collect { image ->
                    viewModel.onCustomSpoilerChanged(image.configured ?: false, busy = image.busy)
                }
            }
            spoiler.events.collect { event ->
                if (event != CustomSpoilerEvent.FAILED) preview.refresh()
                view.showCustomSpoilerEvent(event)
            }
        }
    }
}

/**
 * The page's one widget preview, a View of [factory]: built on first use with the state saved before, then reused,
 * stopped with the entry and closed when the composition drops it, as `SettingsFragment` keeps its preview.
 */
private class SettingsWidgetPreview(val factory: WidgetPreviewFactory, private var savedState: Bundle?) {
    private var preview: WidgetPreview? = null
    private var boundSettings: WidgetPreviewSettings? = null

    @Composable
    fun Slot(settings: WidgetPreviewSettings) {
        val scope = rememberCoroutineScope()
        AndroidView(
            factory = { context -> obtain(context, scope, settings).view },
            modifier = Modifier.fillMaxWidth(),
            onRelease = { release() },
            update = { bind(settings) },
        )
    }

    private fun obtain(context: Context, scope: CoroutineScope, settings: WidgetPreviewSettings): WidgetPreview =
        preview ?: factory.create(context, scope, settings).also {
            savedState?.let(it::restoreState)
            preview = it
            bind(settings)
        }

    private fun bind(settings: WidgetPreviewSettings) {
        val shown = preview ?: return
        if (settings == boundSettings) return
        boundSettings = settings
        shown.bind(settings)
    }

    fun refresh() {
        preview?.refresh()
    }

    fun stop() {
        preview?.stop()
    }

    fun save(): Bundle? = preview?.saveState() ?: savedState

    /** Called when the composition drops the preview and when the entry leaves; the second call finds nothing. */
    fun release() {
        val shown = preview ?: return
        savedState = shown.saveState() ?: savedState
        shown.close()
        preview = null
        boundSettings = null
    }
}

@Composable
private fun rememberWidgetPreview(context: Context): SettingsWidgetPreview {
    val factory = remember(context) { KoinStarter.ensureStarted(context).get<WidgetPreviewFactory>() }
    val preview = rememberSaveable(
        factory,
        saver = Saver<SettingsWidgetPreview, Bundle>(save = { it.save() }, restore = { SettingsWidgetPreview(factory, it) }),
    ) { SettingsWidgetPreview(factory, null) }
    LifecycleStartEffect(preview) { onStopOrDispose { preview.stop() } }
    DisposableEffect(preview) { onDispose { preview.release() } }
    return preview
}

/** The shown dialog by name, as `SettingsFragment` keeps it in its saved state. */
private val DialogsSaver = Saver<SettingsDialogState, String>(
    save = { it.save() },
    restore = { SettingsDialogState.restore(it) },
)

/** «Журнал ошибок» (row 11): the journal goes to the clipboard; Android 13 and later confirm a copy themselves. */
@Composable
private fun DiagnosticsEntry(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: DiagnosticsViewModel = entryViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val actions = remember(viewModel, onBack, context) {
        DiagnosticsActions(
            onBack = onBack,
            onCopy = {
                context.copyDiagnosticsJournal(viewModel.exportText()) { message ->
                    scope.launch { snackbars.showSnackbar(message) }
                }
            },
            onClear = viewModel::clear,
        )
    }
    DiagnosticsScreen(state, actions, viewModel::formatTime, snackbarHostState = snackbars)
}

/**
 * «Выгрузить в .ics» (S6): the date range picker for «Свои даты» runs on the activity's fragment manager, listened to
 * again after recreation and closed with the sheet; the written file is shared or opened through the FileProvider
 * intents `IcsExportBottomSheet` sends.
 */
@Composable
private fun IcsExportEntry() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: IcsExportViewModel = entryViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dates = (activity as? FragmentActivity)?.supportFragmentManager
    DisposableEffect(viewModel, dates) {
        dates?.listenToIcsDatePicker(viewModel::onDates)
        onDispose { if (activity?.isChangingConfigurations != true) dates?.closeIcsDatePicker() }
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    IcsExportEvent.PickDates -> dates?.showIcsDatePicker(viewModel::onDates)
                }
            }
        }
    }
    val actions = remember(viewModel, context) {
        IcsExportActions(
            onChoose = { kind -> viewModel.choose(kind) },
            onSend = { file -> context.shareIcs(file) },
            onOpen = { file -> context.openIcs(file) },
            onChooseAnother = { viewModel.chooseAnother() },
            onRetry = { viewModel.retry() },
        )
    }
    IcsExportSheetContent(state, actions, canOpen = { file -> context.canOpenIcs(file) })
}

/** The picker belongs to the sheet: selecting a tab or Back closes both, as a child of the Fragment sheet did. */
private fun FragmentManager.closeIcsDatePicker() {
    (findFragmentByTag(ICS_DATES_TAG) as? DialogFragment)?.dismissAllowingStateLoss()
}

private fun snackbar(view: View, message: Int, duration: Int) {
    Snackbar.make(view, message, duration).show()
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
