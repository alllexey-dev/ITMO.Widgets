package dev.alllexey.itmowidgets.feature.settings.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.core.ui.navigation.ScreenTransitionHost
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.ui.navigation.dismissOverlays
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.SettingsLevelMotion
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.openLink
import dev.alllexey.itmowidgets.core.ui.permission.RequestNotificationPermission
import dev.alllexey.itmowidgets.core.ui.permission.openAppNotificationSettings
import dev.alllexey.itmowidgets.core.ui.permission.openNotificationSettingsIfLocked
import dev.alllexey.itmowidgets.core.ui.permission.requestNotifications
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropResult
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerImagePicker
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Every settings page (`@id/settings` with its `settings_page` argument): `SettingsScreen` from
 * `:shared:feature-settings` over the Koin `SettingsViewModel`, with the page's widget preview, a View of
 * [WidgetPreviewFactory], in the screen's slot. The host keeps what only Android does: the permission and result
 * launchers, system pages, navigation between pages and the postponed enter until the first rows and the preview are
 * ready. The screen draws the page's dialogs; the host opens the background work and calendar ones from its events and
 * permission results and keeps the shown one in its saved state.
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject lateinit var previewFactory: WidgetPreviewFactory
    private var widgetPreview: WidgetPreview? = null
    private var boundPreviewSettings: WidgetPreviewSettings? = null
    private var previewState: Bundle? = null

    private val viewModel: SettingsViewModel by viewModel()
    private val spoilerViewModel: CustomSpoilerViewModel by viewModel()

    private val actions = SettingsActions(
        onBack = { closeScreen() },
        onToggle = { id, checked -> viewModel.onToggleChanged(id, checked) },
        onChoice = { id, optionKey -> viewModel.onChoiceChanged(id, optionKey) },
        onNavigate = { page -> findNavController().navigate(R.id.settings, bundleOf(SettingsPage.ARGUMENT to page.name)) },
        onAction = { id -> viewModel.onAction(id) },
        onAllowBackgroundWork = { requireActivity().openBackgroundWorkSettings() },
        onAllowCalendarAccess = { askCalendarAccess() },
        onOpenAppSettings = { openAppSettings() },
    )

    /** Restored in [onCreate]: a permission result can open a dialog before the page is first composed. */
    private var dialogs = SettingsDialogState()

    private val spoilerImagePicker = SpoilerImagePicker(this) { result ->
        spoilerViewModel.onCustomSpoilerCropped(result, ::showImageError)
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(RequestNotificationPermission()) { granted ->
            requireActivity().onSettingsNotificationPermission(granted, viewModel)
        }

    /** The calendar permission dialog is on screen for turning sync on; survives recreation. */
    private var pendingCalendarAccess = false

    private val calendarPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (!pendingCalendarAccess) return@registerForActivityResult
            pendingCalendarAccess = false
            when (requireActivity().calendarAccessAnswer(results)) {
                CalendarAccessAnswer.GRANTED -> viewModel.onCalendarAccessGranted()
                CalendarAccessAnswer.LOCKED -> showCalendarAccessDialog(locked = true)
                CalendarAccessAnswer.DENIED -> requireView().showCalendarAccessDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCalendarAccess = savedInstanceState?.getBoolean(PENDING_CALENDAR_ACCESS) ?: false
        dialogs = SettingsDialogState.restore(savedInstanceState?.getString(DIALOG))
        enterTransition = if (arguments?.getBoolean(ScreenTransitionHost.ARG_OVERLAY_ROOT) == true) null
        else SettingsLevelMotion.transition(forward = true)
        exitTransition = SettingsLevelMotion.transition(forward = true)
        returnTransition = SettingsLevelMotion.transition(forward = false)
        reenterTransition = SettingsLevelMotion.transition(forward = false)
        // Start local observation before inflating the page and avoid an unknown permission row.
        viewModel.onNotificationPermissionChanged(
            NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        )
        viewModel.onBackgroundWorkChanged()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = itmoComposeView {
        val state by viewModel.uiState.collectAsState()
        SettingsScreen(
            state = state,
            actions = actions,
            widgetPreview = { settings ->
                AndroidView(
                    factory = { obtainPreview(settings).view },
                    modifier = Modifier.fillMaxWidth(),
                    onRelease = { releasePreview() },
                    update = { bindPreview(settings) },
                )
            },
            dialogs = dialogs,
        )
    }.apply {
        // The page slides as one surface in the shared-axis transitions.
        isTransitionGroup = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        val page = viewModel.uiState.value.page
        if (page == SettingsPage.ROOT) {
            previewFactory.preload(requireContext(), lifecycleScope)
        }

        previewState = savedInstanceState?.getBundle(PREVIEW_STATE) ?: previewState

        if (page == SettingsPage.QR_WIDGET) {
            observeCustomSpoiler()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = viewModel.uiState.first { it.loaded }
            // Widget pages build their preview in the same state as their first rows.
            loaded.previewSettings?.let { obtainPreview(it).awaitReady() }
            // Enter with final local values and an already drawn QR image, not a loading frame.
            view.doOnPreDraw {
                startPostponedEnterTransition()
                (parentFragment as? ScreenTransitionHost)?.onContentReady()
            }
        }

        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { event ->
                when (event) {
                    SettingsEvent.WidgetsRefreshStarted -> requireView().showWidgetsRefreshStarted()
                    SettingsEvent.OpenNotificationSettings -> requireContext().openAppNotificationSettings()
                    SettingsEvent.RequestNotificationPermission ->
                        requireContext().requestNotifications(notificationPermissionLauncher)
                    SettingsEvent.ChooseCustomSpoiler -> chooseCustomSpoiler()
                    SettingsEvent.ResetCustomSpoiler -> spoilerViewModel.resetImage()
                    SettingsEvent.OpenDiagnostics -> openScreen(AppScreen.DIAGNOSTICS)
                    // The root gate already switched to the flow; the overlay just has to leave.
                    SettingsEvent.CloseOverlays -> dismissOverlays()
                    SettingsEvent.OpenBackgroundWorkSettings -> requireActivity().openBackgroundWorkSettings()
                    SettingsEvent.ShowBackgroundWorkHint -> dialogs.show(SettingsDialog.BackgroundWorkHint)
                    SettingsEvent.RequestQrTile -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requireActivity().requestAddQrTile(viewModel::onQrTileResult)
                    }
                    SettingsEvent.RequestCalendarAccess -> requestCalendarAccess()
                    SettingsEvent.OpenIcsExport -> if (childFragmentManager.findFragmentByTag(IcsExportBottomSheet.TAG) == null) {
                        IcsExportBottomSheet().show(childFragmentManager, IcsExportBottomSheet.TAG)
                    }
                    is SettingsEvent.OpenWebPage -> openLink(BuildConfig.WIDGETS_BASE_URL + event.path, requireView())
                    // A switch always shows the state, so one the action did not change has nothing to undo.
                    is SettingsEvent.ShowMessage -> Snackbar.make(
                        requireView(),
                        event.text.resolve(requireContext()),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    is SettingsEvent.ShowError -> Snackbar.make(
                        requireView(),
                        event.error.messageRes(),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun observeCustomSpoiler() {
        spoilerViewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                viewModel.onCustomSpoilerChanged(state.configured ?: false, busy = state.busy)
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        spoilerViewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { event ->
                if (event != CustomSpoilerEvent.FAILED) widgetPreview?.refresh()
                requireView().showCustomSpoilerEvent(event)
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /** The page's one widget preview: built on first use with the state saved before, then reused. */
    private fun obtainPreview(settings: WidgetPreviewSettings): WidgetPreview =
        widgetPreview ?: previewFactory.create(requireContext(), viewLifecycleOwner.lifecycleScope, settings).also {
            previewState?.let(it::restoreState)
            widgetPreview = it
            bindPreview(settings)
        }

    private fun bindPreview(settings: WidgetPreviewSettings) {
        val preview = widgetPreview ?: return
        if (settings == boundPreviewSettings) return
        boundPreviewSettings = settings
        preview.bind(settings)
    }

    /** Called when the composition drops the preview and from [onDestroyView]; the second call finds nothing. */
    private fun releasePreview() {
        val preview = widgetPreview ?: return
        previewState = preview.saveState() ?: previewState
        preview.close()
        widgetPreview = null
        boundPreviewSettings = null
    }

    override fun onResume() {
        super.onResume()
        viewModel.onNotificationPermissionChanged(
            NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        )
        // Back from the system page, the row leaves by itself once Android lets the app work in the background.
        viewModel.onBackgroundWorkChanged()
    }

    override fun onStop() {
        widgetPreview?.stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(PREVIEW_STATE, widgetPreview?.saveState() ?: previewState)
        outState.putBoolean(PENDING_CALENDAR_ACCESS, pendingCalendarAccess)
        outState.putString(DIALOG, dialogs.save())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePreview()
    }

    private fun chooseCustomSpoiler() {
        if (spoilerViewModel.uiState.value.busy) return
        spoilerImagePicker.launch()
    }

    private fun showImageError() {
        view?.let { Snackbar.make(it, R.string.settings_qr_custom_image_failed, Snackbar.LENGTH_LONG).show() }
    }

    private fun requestCalendarAccess() {
        when (requireActivity().calendarAccessStep()) {
            CalendarAccessStep.GRANTED -> viewModel.onCalendarAccessGranted()
            CalendarAccessStep.ASK -> askCalendarAccess()
            CalendarAccessStep.EXPLAIN -> showCalendarAccessDialog(locked = false)
        }
    }

    private fun askCalendarAccess() {
        pendingCalendarAccess = true
        calendarPermissionLauncher.launch(CALENDAR_PERMISSIONS)
    }

    /**
     * Why the app asks for the calendar: «Разрешить» asks Android, or, after a refusal for good ([locked]), «Открыть
     * настройки» opens the app's system page; «Не сейчас» and dismissing do nothing.
     */
    internal fun showCalendarAccessDialog(locked: Boolean) {
        dialogs.show(SettingsDialog.CalendarAccess(locked))
    }

    private companion object {
        const val PREVIEW_STATE = "widget_preview_state"
        const val PENDING_CALENDAR_ACCESS = "pending_calendar_access"
        const val DIALOG = "settings_dialog"
    }
}

/** The two permissions calendar sync needs, asked together. */
internal val CALENDAR_PERMISSIONS = arrayOf(
    android.Manifest.permission.READ_CALENDAR,
    android.Manifest.permission.WRITE_CALENDAR
)

/** How turning calendar sync on starts: straight on, the system dialog, or a short explanation first. */
internal enum class CalendarAccessStep { GRANTED, ASK, EXPLAIN }

/** Granted: straight on. Otherwise a short explanation first when Android suggests one, then the system dialog. */
internal fun Activity.calendarAccessStep(): CalendarAccessStep = when {
    CALENDAR_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED } ->
        CalendarAccessStep.GRANTED
    !ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.WRITE_CALENDAR) ->
        CalendarAccessStep.ASK
    else -> CalendarAccessStep.EXPLAIN
}

/** What the system dialog's [results] mean: sync goes on, the locked explanation, or a short denial message. */
internal enum class CalendarAccessAnswer { GRANTED, LOCKED, DENIED }

internal fun Activity.calendarAccessAnswer(results: Map<String, Boolean>): CalendarAccessAnswer = when {
    results.isNotEmpty() && results.values.all { it } -> CalendarAccessAnswer.GRANTED
    // A refusal without a dialog means the permission is locked; only the app's system page can undo that.
    !ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.WRITE_CALENDAR) ->
        CalendarAccessAnswer.LOCKED
    else -> CalendarAccessAnswer.DENIED
}

/** The notification dialog's answer: a lock opens the system page, and the row follows the system switch. */
internal fun Activity.onSettingsNotificationPermission(granted: Boolean, viewModel: SettingsViewModel) {
    openNotificationSettingsIfLocked(granted)
    viewModel.onNotificationPermissionChanged(NotificationManagerCompat.from(this).areNotificationsEnabled())
}

/** Turning calendar sync on was refused once more; the dialog may still appear next time. */
internal fun View.showCalendarAccessDenied() {
    Snackbar.make(this, R.string.calendar_access_denied, Snackbar.LENGTH_SHORT).show()
}

internal fun View.showWidgetsRefreshStarted() {
    Snackbar.make(this, R.string.settings_refresh_widgets_started, Snackbar.LENGTH_SHORT).show()
}

/** What became of the custom spoiler image: saved, reset or failed. */
internal fun View.showCustomSpoilerEvent(event: CustomSpoilerEvent) {
    val message = when (event) {
        CustomSpoilerEvent.SAVED -> R.string.settings_qr_custom_image_saved
        CustomSpoilerEvent.RESET -> R.string.settings_qr_custom_image_reset
        CustomSpoilerEvent.FAILED -> R.string.settings_qr_custom_image_failed
    }
    Snackbar.make(this, message, Snackbar.LENGTH_LONG).show()
}

/** The picked and cropped spoiler image is saved; [onFailed] says a failed pick or crop; a cancel does nothing. */
internal fun CustomSpoilerViewModel.onCustomSpoilerCropped(result: SpoilerCropResult, onFailed: () -> Unit) {
    when (result) {
        is SpoilerCropResult.Image -> saveImage(result.uri.toString())
        SpoilerCropResult.Failed -> onFailed()
        SpoilerCropResult.Cancelled -> Unit
    }
}
