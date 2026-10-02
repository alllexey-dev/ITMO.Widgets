package dev.alllexey.itmowidgets.feature.settings.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.GroupPosition
import dev.alllexey.itmowidgets.core.ui.bindGroupPosition
import dev.alllexey.itmowidgets.core.ui.expandToContent
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.databinding.ItemIcsRangeBinding
import dev.alllexey.itmowidgets.databinding.SheetIcsExportBinding
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsRangeOption
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * «Выгрузить в .ics»: the range with its days, then the file to send or open. Choice, loading, the file, an empty
 * range and a failure take the same area, so the sheet does not jump between them.
 */
@AndroidEntryPoint
class IcsExportBottomSheet : BottomSheetDialogFragment() {
    private var _binding: SheetIcsExportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: IcsExportViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetIcsExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        expandToContent()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rangeRows().forEachIndexed { index, row ->
            row.root.bindGroupPosition(
                GroupPosition.of(index, RANGES),
                surface = com.google.android.material.R.attr.colorSurfaceContainerHigh
            )
        }
        binding.state.stateAction.isVisible = true
        listenToIcsDatePicker(viewModel::onDates)
        viewModel.state.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { event ->
            when (event) {
                IcsExportEvent.PickDates -> showIcsDatePicker(viewModel::onDates)
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(state: IcsExportUiState) = with(binding) {
        // Invisible, not gone: the area keeps the tallest state's height, which at a large font scale is the
        // list of ranges rather than the minimum height.
        ranges.isInvisible = state !is IcsExportUiState.Choose
        preparing.isInvisible = state != IcsExportUiState.Preparing
        ready.isInvisible = state !is IcsExportUiState.Ready
        this.state.root.isInvisible = state != IcsExportUiState.Empty && state !is IcsExportUiState.Failed
        when (state) {
            is IcsExportUiState.Choose -> rangeRows().zip(state.options).forEach { (row, option) -> bind(row, option) }
            IcsExportUiState.Preparing -> Unit
            is IcsExportUiState.Ready -> {
                val count = resources.getQuantityString(R.plurals.schedule_lesson_count, state.file.lessons, state.file.lessons)
                readySummary.text = getString(R.string.ics_ready_summary, count, state.dates.resolve(requireContext()))
                send.setOnClickListener { shareIcs(state.file) }
                open.isVisible = canOpenIcs(state.file)
                open.setOnClickListener { openIcs(state.file) }
            }
            IcsExportUiState.Empty -> showState(R.drawable.ic_event_note, getString(R.string.ics_empty), R.string.ics_pick_other) {
                viewModel.chooseAnother()
            }
            is IcsExportUiState.Failed -> showState(R.drawable.ic_error_rounded, getString(state.error.messageRes()), R.string.common_retry) {
                viewModel.retry()
            }
        }
    }

    private fun bind(row: ItemIcsRangeBinding, option: IcsRangeOption) {
        row.rangeTitle.text = option.title.resolve(requireContext())
        row.rangeDates.text = option.dates.resolve(requireContext())
        row.root.contentDescription = "${row.rangeTitle.text}, ${row.rangeDates.text}"
        row.root.setOnClickListener { viewModel.choose(option.kind) }
    }

    private fun showState(icon: Int, title: String, action: Int, onAction: () -> Unit) = with(binding.state) {
        stateIcon.setImageResource(icon)
        stateTitle.text = title
        stateDescription.isVisible = false
        stateAction.setText(action)
        stateAction.setOnClickListener { onAction() }
    }

    private fun rangeRows() = with(binding) { listOf(rangeWeek, rangeTwoWeeks, rangeSemester, rangeCustom) }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "IcsExportBottomSheet"
        private const val RANGES = 4
    }
}
