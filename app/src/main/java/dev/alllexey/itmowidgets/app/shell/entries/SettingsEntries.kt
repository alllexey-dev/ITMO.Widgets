package dev.alllexey.itmowidgets.app.shell.entries

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.SettingsScreenArgs
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.copyToClipboard
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPinRequester
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import dev.alllexey.itmowidgets.feature.settings.ui.CALENDAR_PERMISSIONS
import dev.alllexey.itmowidgets.feature.settings.ui.CalendarAccessAnswer
import dev.alllexey.itmowidgets.feature.settings.ui.CalendarAccessStep
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsActions
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsDialog
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsDialogState
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsScreen
import dev.alllexey.itmowidgets.feature.settings.ui.calendarAccessAnswer
import dev.alllexey.itmowidgets.feature.settings.ui.calendarAccessStep
import dev.alllexey.itmowidgets.feature.settings.ui.canOpenIcs
import dev.alllexey.itmowidgets.feature.settings.ui.closeIcsDatePicker
import dev.alllexey.itmowidgets.feature.settings.ui.copyDiagnosticsJournal
import dev.alllexey.itmowidgets.feature.settings.ui.diagnostics.DiagnosticsActions
import dev.alllexey.itmowidgets.feature.settings.ui.diagnostics.DiagnosticsScreen
import dev.alllexey.itmowidgets.feature.settings.ui.ics.IcsExportActions
import dev.alllexey.itmowidgets.feature.settings.ui.ics.IcsExportSheetContent
import dev.alllexey.itmowidgets.feature.settings.ui.listenToIcsDatePicker
import dev.alllexey.itmowidgets.feature.settings.ui.onCustomSpoilerCropped
import dev.alllexey.itmowidgets.feature.settings.ui.openIcs
import dev.alllexey.itmowidgets.feature.settings.ui.requestAddQrTile
import dev.alllexey.itmowidgets.feature.settings.ui.shareIcs
import dev.alllexey.itmowidgets.feature.settings.ui.showCalendarAccessDenied
import dev.alllexey.itmowidgets.feature.settings.ui.showCustomSpoilerEvent
import dev.alllexey.itmowidgets.feature.settings.ui.showIcsDatePicker
import dev.alllexey.itmowidgets.feature.settings.ui.showWidgetsRefreshStarted
import dev.alllexey.itmowidgets.feature.settings.ui.widgets.WidgetsGalleryActions
import dev.alllexey.itmowidgets.feature.settings.ui.widgets.WidgetsGalleryScreen
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

/**
 * The settings keys: every settings page under its `SettingsScreenArgs.PAGE`, the error
 * journal and the `.ics` sheet. A page's rows open the next page, the journal and the sheet through the navigator;
 * the permission, picker and system-page launchers go through the helpers of `feature/settings/ui/`.
 */
internal fun EntryRegistry.Builder.settingsEntries() {
    entry<AppRoutes.Settings>(args = { bundleOf(SettingsScreenArgs.PAGE to it.page) }) { key, navigator ->
        when (SettingsPage.fromArgument(key.page)) {
            SettingsPage.SERVICES, SettingsPage.PRIVACY -> ConnectionEntry(key, navigator)
            SettingsPage.NOTIFICATIONS -> NotificationSettingsEntry(key, navigator)
            else -> SettingsEntry(key, navigator)
        }
    }
    entry<AppRoutes.Diagnostics> { key, navigator -> DiagnosticsEntry(onBack = { navigator.close(key) }) }
    entry<AppRoutes.IcsExport> { _, _ -> IcsExportEntry() }
}

/**
 * One settings page: the shown dialog and a pending calendar request in this entry's saved state, the row states
 * refreshed on every resume, and on the widgets gallery the custom spoiler image with its own ViewModel. A sub-page opens as another [AppRoutes.Settings].
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
    // Only the widgets gallery reads the spoiler image (the pass sheet).
    val spoiler: CustomSpoilerViewModel? = if (page == SettingsPage.WIDGETS) entryViewModel() else null
    val dialogs = rememberSaveable(saver = DialogsSaver) { SettingsDialogState() }
    var pendingCalendarAccess by rememberSaveable { mutableStateOf(false) }

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
        if (page == SettingsPage.WIDGETS) countPlacedWidgets(context, viewModel)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    SettingsEvent.ChooseCustomSpoiler -> if (spoiler?.uiState?.value?.busy == false) pickSpoilerImage()
                    SettingsEvent.ResetCustomSpoiler -> spoiler?.resetImage()
                    SettingsEvent.OpenDiagnostics -> navigator.open(AppRoutes.Diagnostics)
                    // The root gate already switched to the flow; the overlay just has to leave.
                    SettingsEvent.CloseOverlays -> navigator.dismissOverlays()
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
                    // Android 13 and later confirm a copy themselves.
                    is SettingsEvent.CopyText ->
                        context.copyToClipboard(context.getString(R.string.app_name), event.text) {
                            Snackbar.make(view, event.confirmation.resolve(context), Snackbar.LENGTH_SHORT).show()
                        }
                    else -> showSettingsMessage(event, context, view, platform)
                }
            }
        }
    }
    // A new spoiler image redraws the pass previews of the gallery.
    var spoilerRevision by rememberSaveable { mutableStateOf(0) }
    if (spoiler != null) CustomSpoilerEffects(spoiler, viewModel, onImageChanged = { spoilerRevision++ }, view)

    val actions = remember(viewModel, navigator, key, activity, platform) {
        SettingsActions(
            onBack = { navigator.close(key) },
            onToggle = { id, checked -> viewModel.onToggleChanged(id, checked) },
            onChoice = { id, optionKey -> viewModel.onChoiceChanged(id, optionKey) },
            onNavigate = { next -> navigator.open(AppRoutes.Settings(next.name)) },
            onAction = { id -> viewModel.onAction(id) },
            onAllowCalendarAccess = askCalendarAccess,
            onOpenAppSettings = { platform.openAppSettings() },
        )
    }
    if (page == SettingsPage.WIDGETS) {
        WidgetsGalleryEntry(state, actions, viewModel, view, spoilerRevision)
    } else {
        SettingsScreen(state = state, actions = actions, dialogs = dialogs)
    }
}

/**
 * The widgets gallery: each card and the open sheet draw their own real widget preview, and `Добавить` pins one
 * through the launcher; a pinned widget is counted again at once.
 */
@Composable
private fun WidgetsGalleryEntry(
    state: SettingsUiState,
    actions: SettingsActions,
    viewModel: SettingsViewModel,
    view: View,
    spoilerRevision: Int,
) {
    val context = LocalContext.current
    val pins = remember(context) { WidgetPinRequester(context) }
    DisposableEffect(pins) {
        pins.start { countPlacedWidgets(context, viewModel) }
        onDispose { pins.stop() }
    }
    val factory = remember(context) { KoinStarter.ensureStarted(context).get<WidgetPreviewFactory>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(factory) { factory.preload(context, scope) }
    WidgetsGalleryScreen(
        state = state,
        actions = actions,
        galleryActions = WidgetsGalleryActions(
            onAdd = { format ->
                if (!pins.isSupported || !pins.request(WidgetProviders.of(format))) {
                    snackbar(view, R.string.widget_pin_failed, Snackbar.LENGTH_SHORT)
                }
            },
        ),
        widgetPreview = { settings, modifier -> key(spoilerRevision) { GalleryPreview(factory, settings, modifier) } },
    )
}

/** One real widget on the sample data, its own [WidgetPreview] for as long as it is on screen. */
@Composable
private fun GalleryPreview(factory: WidgetPreviewFactory, settings: WidgetPreviewSettings, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<WidgetPreview?>(null) }
    AndroidView(
        factory = { context -> factory.create(context, scope, settings).also { preview = it }.view },
        modifier = modifier,
        onRelease = { preview?.close() },
        update = { preview?.bind(settings) },
    )
}

/** How many widgets of each format the launcher holds now. */
private fun countPlacedWidgets(context: Context, viewModel: SettingsViewModel) {
    val manager = AppWidgetManager.getInstance(context)
    WidgetFormat.entries.forEach { format ->
        viewModel.onPlacedWidgets(format, manager.getAppWidgetIds(ComponentName(context, WidgetProviders.of(format))).size)
    }
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
 * The gallery's custom spoiler image: its state drives the image rows, and a saved or reset image redraws the pass
 * previews and says so while the page is started.
 */
@Composable
private fun CustomSpoilerEffects(
    spoiler: CustomSpoilerViewModel,
    viewModel: SettingsViewModel,
    onImageChanged: () -> Unit,
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
                if (event != CustomSpoilerEvent.FAILED) onImageChanged()
                view.showCustomSpoilerEvent(event)
            }
        }
    }
}

/** The shown dialog by name, kept in the entry's saved state. */
private val DialogsSaver = Saver<SettingsDialogState, String>(
    save = { it.save() },
    restore = { SettingsDialogState.restore(it) },
)

/** «Журнал ошибок»: the journal goes to the clipboard; Android 13 and later confirm a copy themselves. */
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
 * «Выгрузить в .ics»: the date range picker for «Свои даты» runs on the activity's fragment manager, listened to
 * again after recreation and closed with the sheet; the written file is shared or opened through the FileProvider
 * intents.
 */
@Composable
private fun IcsExportEntry() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: IcsExportViewModel = entryViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dates = activity as? AppCompatActivity
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
