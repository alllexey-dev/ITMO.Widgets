package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import dev.alllexey.itmowidgets.databinding.FragmentSettingsBinding
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject lateinit var previewFactory: WidgetPreviewFactory
    private var widgetPreview: WidgetPreview? = null
    private var previewState: Bundle? = null

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModel()
    private val spoilerViewModel: CustomSpoilerViewModel by viewModel()

    private var renderer: SettingsRenderer? = null

    private val spoilerImagePicker = SpoilerImagePicker(this) { result ->
        when (result) {
            is SpoilerCropResult.Image -> spoilerViewModel.saveImage(result.uri.toString())
            SpoilerCropResult.Failed -> showImageError()
            SpoilerCropResult.Cancelled -> Unit
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(RequestNotificationPermission()) { granted ->
            requireActivity().openNotificationSettingsIfLocked(granted)
            viewModel.onNotificationPermissionChanged(
                NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
            )
        }

    /** The calendar permission dialog is on screen for turning sync on; survives recreation. */
    private var pendingCalendarAccess = false

    private val calendarPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (!pendingCalendarAccess) return@registerForActivityResult
            pendingCalendarAccess = false
            if (results.isNotEmpty() && results.values.all { it }) {
                viewModel.onCalendarAccessGranted()
                return@registerForActivityResult
            }
            restoreRenderedValues()
            // A refusal without a dialog means the permission is locked; only the app's system page can undo that.
            if (!shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_CALENDAR)) {
                showCalendarAccessDialog(locked = true, onAllow = {}, onCancel = ::restoreRenderedValues)
            } else {
                Snackbar.make(binding.root, R.string.calendar_access_denied, Snackbar.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCalendarAccess = savedInstanceState?.getBoolean(PENDING_CALENDAR_ACCESS) ?: false
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
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        val page = viewModel.uiState.value.page
        if (page == SettingsPage.ROOT) {
            previewFactory.preload(requireContext(), lifecycleScope)
        }

        previewState = savedInstanceState?.getBundle(PREVIEW_STATE) ?: previewState
        binding.settingsTitle.text = page.title.resolve(requireContext())
        binding.backButton.setOnClickListener { closeScreen() }
        val initialBottomPadding = binding.sectionsContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.sectionsContainer) { content, insets ->
            content.updatePadding(
                bottom = initialBottomPadding +
                    insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.sectionsContainer)

        renderer = SettingsRenderer(
            container = binding.sectionsContainer,
            onToggle = ::onToggleChanged,
            onChoice = ::showChoiceDialog,
            onNavigate = { page ->
                findNavController().navigate(
                    R.id.settings,
                    bundleOf(SettingsPage.ARGUMENT to page.name)
                )
            },
            onAction = viewModel::onAction
        )

        viewModel.uiState
            .map { state -> state.previewSettings }
            .distinctUntilChanged()
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { settings -> settings?.let(::renderPreview) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        if (page == SettingsPage.QR_WIDGET) {
            observeCustomSpoiler()
        }

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::renderSections)
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = viewModel.uiState.first { it.loaded }
            renderSections(loaded)
            // Widget pages build their preview in the same state as their first rows.
            loaded.previewSettings?.let { renderPreview(it).awaitReady() }
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
                    SettingsEvent.WidgetsRefreshStarted -> Snackbar.make(
                        binding.root,
                        R.string.settings_refresh_widgets_started,
                        Snackbar.LENGTH_SHORT
                    ).show()
                    SettingsEvent.OpenNotificationSettings -> requireContext().openAppNotificationSettings()
                    SettingsEvent.RequestNotificationPermission ->
                        requireContext().requestNotifications(notificationPermissionLauncher)
                    SettingsEvent.ChooseCustomSpoiler -> chooseCustomSpoiler()
                    SettingsEvent.ResetCustomSpoiler -> spoilerViewModel.resetImage()
                    SettingsEvent.OpenDiagnostics -> openScreen(AppScreen.DIAGNOSTICS)
                    // The root gate already switched to the flow; the overlay just has to leave.
                    SettingsEvent.CloseOverlays -> dismissOverlays()
                    SettingsEvent.OpenBackgroundWorkSettings -> requireActivity().openBackgroundWorkSettings()
                    SettingsEvent.ShowBackgroundWorkHint -> showBackgroundWorkHint()
                    SettingsEvent.RequestQrTile -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requireActivity().requestAddQrTile(viewModel::onQrTileResult)
                    }
                    SettingsEvent.RequestCalendarAccess -> requestCalendarAccess()
                    SettingsEvent.OpenIcsExport -> if (childFragmentManager.findFragmentByTag(IcsExportBottomSheet.TAG) == null) {
                        IcsExportBottomSheet().show(childFragmentManager, IcsExportBottomSheet.TAG)
                    }
                    is SettingsEvent.OpenWebPage -> openLink(BuildConfig.WIDGETS_BASE_URL + event.path, binding.root)
                    is SettingsEvent.ShowMessage -> {
                        // A switch the user flipped stays as the state says when the action did not go through.
                        restoreRenderedValues()
                        Snackbar.make(
                            binding.root,
                            event.text.resolve(requireContext()),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    is SettingsEvent.ShowError -> {
                        restoreRenderedValues()
                        Snackbar.make(
                            binding.root,
                            event.error.messageRes(),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
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
                val message = when (event) {
                    CustomSpoilerEvent.SAVED -> R.string.settings_qr_custom_image_saved
                    CustomSpoilerEvent.RESET -> R.string.settings_qr_custom_image_reset
                    CustomSpoilerEvent.FAILED -> R.string.settings_qr_custom_image_failed
                }
                if (event != CustomSpoilerEvent.FAILED) widgetPreview?.refresh()
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun renderSections(state: SettingsUiState) {
        binding.settingsProgress.isVisible = state.page == SettingsPage.PRIVACY &&
            state.loaded && state.sections.isEmpty()
        binding.settingsScroll.isVisible = state.sections.isNotEmpty()
        renderer?.render(state.sections)
    }

    private fun renderPreview(settings: WidgetPreviewSettings): WidgetPreview {
        val preview = widgetPreview ?: previewFactory.create(
            requireContext(), viewLifecycleOwner.lifecycleScope, settings
        ).also {
            previewState?.let(it::restoreState)
            widgetPreview = it
            binding.widgetPreviewContainer.addView(it.view)
        }
        preview.bind(settings)
        binding.widgetPreviewContainer.isVisible = true
        return preview
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
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        previewState = widgetPreview?.saveState() ?: previewState
        widgetPreview?.close()
        widgetPreview = null
        renderer = null
        _binding = null
    }

    private fun onToggleChanged(id: SettingRowId, checked: Boolean) {
        if (id != SettingRowId.CUSTOM_SERVICES || !checked) {
            viewModel.onToggleChanged(id, checked)
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_custom_services_consent_title)
            .setMessage(R.string.settings_custom_services_consent_message)
            .setNegativeButton(R.string.common_cancel) { _, _ -> restoreRenderedValues() }
            .setPositiveButton(R.string.settings_custom_services_enable) { _, _ ->
                viewModel.onToggleChanged(id, true)
            }
            .setOnCancelListener { restoreRenderedValues() }
            .show()
    }

    private fun showBackgroundWorkHint() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_background_work_title)
            .setMessage(R.string.background_work_dialog_message)
            .setNegativeButton(R.string.background_work_later, null)
            .setPositiveButton(R.string.background_work_allow) { _, _ -> requireActivity().openBackgroundWorkSettings() }
            .show()
    }

    private fun restoreRenderedValues() {
        renderer?.render(viewModel.uiState.value.sections)
    }

    private fun showChoiceDialog(item: SettingItem.Choice) {
        val labels = item.options
            .map { option -> option.label.resolve(requireContext()) }
            .toTypedArray()
        val selectedIndex = item.options.indexOfFirst { option ->
            option.key == item.selectedOptionKey
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title.resolve(requireContext()))
            .setSingleChoiceItems(labels, selectedIndex) { dialog, index ->
                viewModel.onChoiceChanged(item.id, item.options[index].key)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun chooseCustomSpoiler() {
        if (spoilerViewModel.uiState.value.busy) return
        spoilerImagePicker.launch()
    }

    private fun showImageError() {
        _binding?.let { Snackbar.make(it.root, R.string.settings_qr_custom_image_failed, Snackbar.LENGTH_LONG).show() }
    }

    /** Granted: straight on. Otherwise a short explanation first when Android suggests one, then the system dialog. */
    private fun requestCalendarAccess() {
        val context = requireContext()
        val granted = CALENDAR_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            viewModel.onCalendarAccessGranted()
            return
        }
        val ask = {
            pendingCalendarAccess = true
            calendarPermissionLauncher.launch(CALENDAR_PERMISSIONS)
        }
        if (!shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_CALENDAR)) {
            ask()
            return
        }
        showCalendarAccessDialog(locked = false, onAllow = ask, onCancel = ::restoreRenderedValues)
    }

    private companion object {
        const val PREVIEW_STATE = "widget_preview_state"
        const val PENDING_CALENDAR_ACCESS = "pending_calendar_access"
        val CALENDAR_PERMISSIONS = arrayOf(
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR
        )
    }
}
