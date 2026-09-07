package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.SettingsLevelMotion
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.databinding.FragmentSettingsBinding
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject lateinit var previewFactory: WidgetPreviewFactory
    private var widgetPreview: WidgetPreview? = null
    private var previewState: Bundle? = null

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()
    private val spoilerViewModel: CustomSpoilerViewModel by viewModels()

    private var renderer: SettingsRenderer? = null

    private val cropImageLauncher = registerForActivityResult(SpoilerCropContract()) { result ->
        when (result) {
            is SpoilerCropResult.Image -> spoilerViewModel.saveImage(result.uri.toString())
            SpoilerCropResult.Failed -> showImageError()
            SpoilerCropResult.Cancelled -> Unit
        }
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropImageLauncher.launch(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = SettingsLevelMotion.transition(forward = true)
        exitTransition = SettingsLevelMotion.transition(forward = true)
        returnTransition = SettingsLevelMotion.transition(forward = false)
        reenterTransition = SettingsLevelMotion.transition(forward = false)
        // Start local observation before inflating the page and avoid an unknown permission row.
        viewModel.onNotificationPermissionChanged(
            NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        )
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
        if (viewModel.page == SettingsPage.ROOT) {
            previewFactory.preload(requireContext(), lifecycleScope)
        }

        previewState = savedInstanceState?.getBundle(PREVIEW_STATE) ?: previewState
        binding.settingsTitle.text = viewModel.page.title.resolve(requireContext())
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
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

        viewModel.previewSettings
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { settings -> settings?.let(::renderPreview) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        if (viewModel.page == SettingsPage.QR_WIDGET) {
            observeCustomSpoiler()
        }

        viewModel.sections
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::renderSections)
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.localSettingsLoaded.first { it }
            renderSections(viewModel.sections.value)
            if (viewModel.page == SettingsPage.QR_WIDGET || viewModel.page == SettingsPage.SCHEDULE_WIDGETS) {
                renderPreview(viewModel.previewSettings.filterNotNull().first()).awaitReady()
            }
            // Enter with final local values and an already drawn QR image, not a loading frame.
            view.doOnPreDraw { startPostponedEnterTransition() }
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
                    SettingsEvent.OpenNotificationSettings -> openNotificationSettings()
                    SettingsEvent.ChooseCustomSpoiler -> chooseCustomSpoiler()
                    SettingsEvent.ResetCustomSpoiler -> spoilerViewModel.resetImage()
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
        spoilerViewModel.state
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

    private fun renderSections(sections: List<SettingSection>) {
        binding.settingsProgress.isVisible = viewModel.page == SettingsPage.PRIVACY &&
            viewModel.localSettingsLoaded.value && sections.isEmpty()
        binding.settingsScroll.isVisible = sections.isNotEmpty()
        renderer?.render(sections)
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
    }

    override fun onStop() {
        widgetPreview?.stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(PREVIEW_STATE, widgetPreview?.saveState() ?: previewState)
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

    private fun onToggleChanged(key: String, checked: Boolean) {
        if (key != SettingsViewModel.KEY_CUSTOM_SERVICES || !checked) {
            viewModel.onToggleChanged(key, checked)
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_custom_services_consent_title)
            .setMessage(R.string.settings_custom_services_consent_message)
            .setNegativeButton(R.string.common_cancel) { _, _ -> restoreRenderedValues() }
            .setPositiveButton(R.string.settings_custom_services_enable) { _, _ ->
                viewModel.onToggleChanged(key, true)
            }
            .setOnCancelListener { restoreRenderedValues() }
            .show()
    }

    private fun restoreRenderedValues() {
        renderer?.render(viewModel.sections.value)
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
                viewModel.onChoiceChanged(item.key, item.options[index].key)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun chooseCustomSpoiler() {
        if (spoilerViewModel.state.value.busy) return
        try {
            imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            showImageError()
        }
    }

    private fun showImageError() {
        _binding?.let { Snackbar.make(it.root, R.string.settings_qr_custom_image_failed, Snackbar.LENGTH_LONG).show() }
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        )
    }

    private companion object {
        const val PREVIEW_STATE = "widget_preview_state"
    }
}
