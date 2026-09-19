package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
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
 * exactly like the place it can be changed later.
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
    private var preview: WidgetPreview? = null

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
        _binding = null
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

        fun newInstance(kind: WidgetKind): WidgetStepFragment = WidgetStepFragment().apply {
            arguments = bundleOf(ARG_KIND to kind.name)
        }
    }
}
