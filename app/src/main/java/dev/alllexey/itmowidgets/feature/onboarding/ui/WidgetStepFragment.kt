package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropContract
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropResult
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.databinding.FragmentOnboardingWidgetBinding
import dev.alllexey.itmowidgets.databinding.ItemSettingRowBinding
import dev.alllexey.itmowidgets.databinding.ItemSettingToggleBinding
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetOption
import dev.alllexey.itmowidgets.feature.onboarding.presentation.options
import dev.alllexey.itmowidgets.feature.onboarding.presentation.textSize
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * One widget: its real preview, the choices that shape it, and a pin to the launcher.
 *
 * The rows are the settings screen's own toggle rows, so a choice made here looks
 * exactly like the place it can be changed later. The QR step also offers the
 * spoiler image through the same picker and crop screen as settings.
 */
@AndroidEntryPoint
class WidgetStepFragment : Fragment() {

    @Inject lateinit var previewFactory: WidgetPreviewFactory

    private var _binding: FragmentOnboardingWidgetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private val kind: WidgetKind by lazy {
        WidgetKind.valueOf(requireNotNull(requireArguments().getString(ARG_KIND)))
    }
    private val rows = linkedMapOf<WidgetOption, ItemSettingToggleBinding>()
    private var textSizeRow: ItemSettingRowBinding? = null
    private var textSize: WidgetTextSize? = null
    private var spoilerRow: ItemSettingRowBinding? = null
    /** The revision the preview last drew; a newer one re-reads the stored image. */
    private var drawnSpoilerRevision: Int? = null
    private var preview: WidgetPreview? = null

    private val cropImageLauncher = registerForActivityResult(SpoilerCropContract()) { result ->
        when (result) {
            is SpoilerCropResult.Image -> viewModel.saveSpoilerImage(result.uri.toString())
            SpoilerCropResult.Failed -> showImageError()
            SpoilerCropResult.Cancelled -> Unit
        }
    }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) cropImageLauncher.launch(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingWidgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewFactory.preload(requireContext(), viewLifecycleOwner.lifecycleScope)
        binding.stepTitle.setText(kind.titleRes())

        kind.options.forEachIndexed { index, option ->
            if (index > 0) layoutInflater.inflate(R.layout.item_setting_divider, binding.settingRows, true)
            val row = ItemSettingToggleBinding.inflate(layoutInflater, binding.settingRows, true)
            row.settingTitle.setText(option.titleRes())
            val description = option.descriptionRes()
            row.settingDescription.isVisible = description != null
            description?.let(row.settingDescription::setText)
            row.root.setOnClickListener { viewModel.setOption(option, !row.settingSwitch.isChecked) }
            rows[option] = row
        }
        if (kind != WidgetKind.QR) {
            layoutInflater.inflate(R.layout.item_setting_divider, binding.settingRows, true)
            // The settings screen's own choice row: title, current value, chevron, dialog.
            val row = ItemSettingRowBinding.inflate(layoutInflater, binding.settingRows, true)
            row.settingTitle.setText(R.string.settings_widget_text_size_title)
            row.settingDescription.isVisible = false
            row.settingChevron.isVisible = true
            row.root.isClickable = true
            row.root.setOnClickListener { chooseTextSize() }
            textSizeRow = row
        } else {
            layoutInflater.inflate(R.layout.item_setting_divider, binding.settingRows, true)
            val row = ItemSettingRowBinding.inflate(layoutInflater, binding.settingRows, true)
            row.settingTitle.setText(R.string.settings_qr_custom_image_title)
            row.settingDescription.isVisible = false
            row.settingChevron.isVisible = true
            row.root.isClickable = true
            row.root.setOnClickListener { chooseSpoilerImage() }
            spoilerRow = row
        }

        binding.pinButton.setOnClickListener { viewModel.pinWidget(kind) }

        viewModel.state
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onStop() {
        preview?.stop()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preview?.close()
        preview = null
        rows.clear()
        textSizeRow = null
        spoilerRow = null
        drawnSpoilerRevision = null
        _binding = null
    }

    /** A custom image offers a replacement or the default; otherwise straight to the picker. */
    private fun chooseSpoilerImage() {
        val state = viewModel.state.value
        if (state.spoilerBusy) return
        if (state.customSpoiler != true) {
            pickSpoilerImage()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_qr_custom_image_title)
            .setItems(
                arrayOf(
                    getString(R.string.onboarding_spoiler_image_replace),
                    getString(R.string.onboarding_spoiler_image_reset)
                )
            ) { dialog, index ->
                if (index == 0) pickSpoilerImage() else viewModel.resetSpoilerImage()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun pickSpoilerImage() {
        try {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: ActivityNotFoundException) {
            showImageError()
        }
    }

    private fun showImageError() {
        _binding?.let {
            Snackbar.make(it.root, R.string.settings_qr_custom_image_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun chooseTextSize() {
        val current = textSize ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_widget_text_size_title)
            .setSingleChoiceItems(
                WidgetTextSize.entries.map { getString(it.labelRes()) }.toTypedArray(),
                WidgetTextSize.entries.indexOf(current)
            ) { dialog, index ->
                viewModel.setTextSize(kind, WidgetTextSize.entries[index])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun render(state: OnboardingUiState) {
        val pinned = kind in state.pinnedWidgets
        binding.pinButton.isVisible = state.pinSupported
        binding.pinHint.isVisible = !state.pinSupported
        binding.pinButton.isEnabled = !pinned
        binding.pinButton.setText(if (pinned) R.string.onboarding_widget_added else R.string.onboarding_widget_add)
        binding.pinButton.setIconResource(if (pinned) R.drawable.ic_check else R.drawable.ic_pin)

        val appearance = state.appearance ?: return
        renderPreview(appearance)
        // Programmatic values must not read back as a user choice.
        rows.forEach { (option, row) -> row.settingSwitch.isChecked = option.isEnabled(appearance) }
        kind.textSize(appearance)?.let { size ->
            textSize = size
            textSizeRow?.settingValue?.setText(size.labelRes())
        }
        spoilerRow?.let { row ->
            val configured = state.customSpoiler
            row.settingValue.isVisible = configured != null
            configured?.let {
                row.settingValue.setText(
                    if (it) R.string.settings_qr_custom_image_selected else R.string.settings_qr_custom_image_default
                )
            }
            // No image without the spoiler; the row dims exactly like the settings row.
            val enabled = appearance.qr.spoilerEnabled && configured != null && !state.spoilerBusy
            row.root.isEnabled = enabled
            row.root.alpha = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA
        }
        if (drawnSpoilerRevision != null && drawnSpoilerRevision != state.spoilerRevision) preview?.refresh()
        drawnSpoilerRevision = state.spoilerRevision
    }

    private fun WidgetTextSize.labelRes(): Int = when (this) {
        WidgetTextSize.NORMAL -> R.string.settings_widget_text_size_normal
        WidgetTextSize.LARGE -> R.string.settings_widget_text_size_large
        WidgetTextSize.EXTRA_LARGE -> R.string.settings_widget_text_size_extra_large
    }

    private fun renderPreview(appearance: WidgetAppearance) {
        val settings = kind.previewSettings(appearance)
        val current = preview ?: previewFactory.create(
            requireContext(), viewLifecycleOwner.lifecycleScope, settings
        ).also {
            preview = it
            binding.widgetPreviewContainer.addView(it.view)
        }
        current.bind(settings)
    }

    private fun WidgetKind.previewSettings(appearance: WidgetAppearance): WidgetPreviewSettings =
        when (this) {
            WidgetKind.SINGLE_LESSON ->
                WidgetPreviewSettings.Schedule(appearance.schedule, ScheduleWidgetFormat.COMPACT)
            WidgetKind.DAY_SCHEDULE ->
                WidgetPreviewSettings.Schedule(appearance.schedule, ScheduleWidgetFormat.FULL)
            WidgetKind.QR -> WidgetPreviewSettings.Qr(appearance.qr)
        }

    private fun WidgetKind.titleRes(): Int = when (this) {
        WidgetKind.SINGLE_LESSON -> R.string.onboarding_compact_widget_title
        WidgetKind.DAY_SCHEDULE -> R.string.onboarding_full_widget_title
        WidgetKind.QR -> R.string.onboarding_qr_widget_title
    }

    private fun WidgetOption.titleRes(): Int = when (this) {
        WidgetOption.COMPACT_NEXT_LESSON_EARLY -> R.string.settings_widget_next_early_title
        WidgetOption.COMPACT_HIDE_TEACHER, WidgetOption.FULL_HIDE_TEACHER -> R.string.settings_widget_hide_teacher_title
        WidgetOption.FULL_HIDE_PAST_LESSONS -> R.string.settings_widget_hide_past_title
        WidgetOption.FULL_SHOW_TOMORROW -> R.string.settings_widget_tomorrow_title
        WidgetOption.QR_DYNAMIC_COLORS -> R.string.settings_qr_dynamic_colors_title
        WidgetOption.QR_SPOILER -> R.string.settings_qr_spoiler_title
    }

    private fun WidgetOption.descriptionRes(): Int? = when (this) {
        WidgetOption.COMPACT_NEXT_LESSON_EARLY -> R.string.settings_widget_next_early_description
        WidgetOption.FULL_SHOW_TOMORROW -> R.string.settings_widget_tomorrow_description
        WidgetOption.QR_SPOILER -> R.string.settings_qr_spoiler_description
        else -> null
    }

    companion object {
        private const val ARG_KIND = "widget_kind"
        private const val ENABLED_ALPHA = 1f
        private const val DISABLED_ALPHA = 0.6f

        fun newInstance(kind: WidgetKind): WidgetStepFragment = WidgetStepFragment().apply {
            arguments = bundleOf(ARG_KIND to kind.name)
        }
    }
}
