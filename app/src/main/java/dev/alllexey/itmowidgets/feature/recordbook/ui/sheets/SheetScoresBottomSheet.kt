package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.ui.expandToContent
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.databinding.SheetScoresSetupBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetText
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresEvent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * «Мои баллы»: downloads a public Google Sheet, finds the own row and total and connects them, asking only when the
 * sheet leaves a choice: the row, the tab, the total. «Изменить итог» opens it at the choice of the total.
 */
@AndroidEntryPoint
class SheetScoresBottomSheet : BottomSheetDialogFragment() {
    private var _binding: SheetScoresSetupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SheetScoresViewModel by viewModel()
    private val adapter = SheetScoresOptionsAdapter()
    private var rows: List<SheetOption.Choice> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetScoresSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        expandToContent()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.subject.text = viewModel.scope.subjectName
        binding.options.adapter = adapter
        binding.searchInput.doAfterTextChanged { showRows() }
        binding.state.stateIcon.setImageResource(R.drawable.ic_error)
        binding.state.stateDescription.isVisible = false
        binding.state.stateAction.setText(R.string.common_retry)
        binding.state.stateAction.setOnClickListener { viewModel.refresh(RefreshMode.Force) }
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { event ->
            when (event) {
                is SheetScoresEvent.SaveFailed ->
                    Snackbar.make(binding.root, event.text.resolve(requireContext()), Snackbar.LENGTH_SHORT).show()
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(state: SheetScoresUiState) = with(binding) {
        loading.isVisible = state == SheetScoresUiState.Loading
        this.state.root.isVisible = state is SheetScoresUiState.Failed
        choice.isVisible = state is SheetScoresUiState.PickRow || state is SheetScoresUiState.PickTab ||
            state is SheetScoresUiState.PickTabRow || state is SheetScoresUiState.PickTotal
        when (state) {
            SheetScoresUiState.Loading -> showChoices(R.string.sheet_scores_title, emptyList())
            SheetScoresUiState.Done -> dismiss()
            is SheetScoresUiState.Failed -> {
                this.state.stateTitle.setText(state.status.textRes() ?: R.string.sheet_scores_offline)
                this.state.stateAction.isVisible = state.status == SheetStatus.NETWORK
            }
            is SheetScoresUiState.PickRow -> showRows(state.candidates)
            is SheetScoresUiState.PickTabRow -> showRows(state.rows)
            is SheetScoresUiState.PickTab -> showChoices(
                R.string.sheet_scores_pick_tab,
                state.tabs.map { tab ->
                    SheetOption.Choice("tab:${tab.gid}", tab.name.ifBlank { getString(R.string.sheet_scores_pick_tab) }) {
                        viewModel.pickTab(tab)
                    }
                }
            )
            is SheetScoresUiState.PickTotal -> showChoices(R.string.sheet_scores_pick_total, totalOptions(state))
        }
    }

    private fun showChoices(prompt: Int, options: List<SheetOption>) = with(binding) {
        rows = emptyList()
        searchLayout.isVisible = false
        this.prompt.setText(prompt)
        empty.setText(R.string.sheet_scores_no_values)
        empty.isVisible = options.isEmpty()
        adapter.submitList(options)
    }

    /** The people to choose from; a long list gets a search by name that keeps its text across states. */
    private fun showRows(matches: List<SheetRowMatch>) = with(binding) {
        rows = matches.map(::rowOption)
        prompt.setText(R.string.sheet_scores_pick_row)
        searchLayout.isVisible = rows.size > SEARCH_FROM
        showRows()
    }

    private fun showRows() = with(binding) {
        if (rows.isEmpty()) return@with
        val query = SheetText.normalize(searchInput.text?.toString().orEmpty()).takeIf { searchLayout.isVisible }
        val shown = if (query.isNullOrEmpty()) rows else rows.filter { query in SheetText.normalize(it.title) }
        empty.setText(R.string.sheet_scores_search_empty)
        empty.isVisible = shown.isEmpty()
        adapter.submitList(shown)
    }

    /** The name in the row with its tab under it, so a long tab name does not bury the name. */
    private fun rowOption(match: SheetRowMatch) = SheetOption.Choice(
        id = "row:${match.tab.gid}:${match.row}",
        title = match.label,
        caption = match.tab.name.takeIf { it.isNotBlank() },
    ) { viewModel.pickRow(match) }

    /** The cells under a heading of their tab. */
    private fun totalOptions(state: SheetScoresUiState.PickTotal): List<SheetOption> {
        val byTab = state.cells.groupBy { it.tab }
        return byTab.flatMap { (tab, cells) ->
            val heading = SheetOption.Section("tab:${tab.gid}", tab.name).takeIf { tab.name.isNotBlank() }
            listOfNotNull(heading) + cells.map { cell -> totalOption(cell, cell == state.selected) }
        }
    }

    private fun totalOption(cell: SheetCell, selected: Boolean) = SheetOption.Choice(
        id = "cell:${cell.tab.gid}:${cell.column}",
        title = requireContext().columnTitle(cell.headerPath, cell.column),
        value = cell.value,
        selected = selected,
    ) { viewModel.pickTotal(cell) }

    override fun onDestroyView() {
        binding.options.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "SheetScoresBottomSheet"
        private const val SEARCH_FROM = 8

        fun newInstance(args: SheetScoresArgs) = SheetScoresBottomSheet().apply { arguments = args.toBundle() }
    }
}
