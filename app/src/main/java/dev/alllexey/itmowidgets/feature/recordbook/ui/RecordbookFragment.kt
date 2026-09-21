package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.applyAppRefreshColors
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.databinding.FragmentRecordbookBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSelection
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class RecordbookFragment : Fragment() {
    private var _binding: FragmentRecordbookBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecordbookViewModel by viewModels()
    private lateinit var adapter: RecordbookAdapter
    private var lastRefreshError: AppError? = null
    private var lastBarsError: AppError? = null
    private var errorSnackbar: Snackbar? = null
    private var selection: RecordbookSelection? = null
    private var renderingBars = false
    private val barsLogin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.refresh()
    }
    private var scrollState: Parcelable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordbookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        if (savedInstanceState != null) scrollState = savedInstanceState.getParcelable("recordbook_scroll")
        adapter = RecordbookAdapter(::openSubject)
        binding.mainRecyclerView.adapter = adapter
        binding.mainRecyclerView.itemAnimator = null
        binding.swipeRefreshLayout.applyAppRefreshColors()
        binding.swipeRefreshLayout.setOnRefreshListener({ viewModel.refresh() })
        binding.stateAction.setOnClickListener { viewModel.refresh() }
        binding.sourceButton.setOnClickListener { showRecordbookSourceInfo(requireContext()) }
        binding.barsChip.setOnCheckedChangeListener { _, checked -> if (!renderingBars) viewModel.setBarsEnabled(checked) }
        viewModel.barsEnabled.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { enabled ->
            renderingBars = true
            binding.barsChip.isChecked = enabled
            renderingBars = false
        }.launchIn(viewLifecycleOwner.lifecycleScope)
        parentFragmentManager.setFragmentResultListener(RecordbookPeriodBottomSheet.RESULT_KEY, viewLifecycleOwner) { _, result ->
            viewModel.selectPeriod(result.getLong(RecordbookPeriodBottomSheet.RESULT_PROGRAM_ID), result.getInt(RecordbookPeriodBottomSheet.RESULT_SEMESTER))
        }
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.ensureDataLoaded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable("recordbook_scroll", _binding?.mainRecyclerView?.layoutManager?.onSaveInstanceState() ?: scrollState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        scrollState = binding.mainRecyclerView.layoutManager?.onSaveInstanceState()
        binding.mainRecyclerView.adapter = null
        errorSnackbar?.dismiss()
        errorSnackbar = null
        lastRefreshError = null
        lastBarsError = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: RecordbookUiState) {
        if (state !is RecordbookUiState.Content) binding.loading.isVisible = state is RecordbookUiState.Loading
        val refreshError = (state as? RecordbookUiState.Content)?.refreshError
        val barsError = (state as? RecordbookUiState.Content)?.barsError
        if (refreshError != lastRefreshError || barsError != lastBarsError) {
            errorSnackbar?.dismiss()
            errorSnackbar = refreshError?.let {
                Snackbar.make(binding.root, getString(R.string.recordbook_refresh_error, getString(it.messageRes())), Snackbar.LENGTH_LONG)
                    .setAction(R.string.common_retry) { viewModel.refresh() }.also(Snackbar::show)
            } ?: barsError?.let { error ->
                recordbookBarsSnackbar(binding.root, error, { viewModel.refresh() }) {
                    barsLogin.launch(Intent(requireContext(), BarsLoginActivity::class.java))
                }
            }
            lastRefreshError = refreshError
            lastBarsError = barsError
        }
        binding.swipeRefreshLayout.isRefreshing = (state as? RecordbookUiState.Content)?.refreshing == true
        when (state) {
            is RecordbookUiState.Loading -> {
                renderPeriod(state.programs, state.selection)
                binding.swipeRefreshLayout.isVisible = false
                binding.stateContainer.isVisible = false
            }
            is RecordbookUiState.Content -> {
                renderPeriod(state.programs, state.selection)
                val currentBinding = binding
                adapter.submitData(state.subjects, state.sport, state.barsApplied) {
                    if (_binding !== currentBinding || viewModel.uiState.value != state) return@submitData
                    currentBinding.loading.isVisible = false
                    currentBinding.swipeRefreshLayout.isVisible = state.subjects.isNotEmpty()
                    currentBinding.stateContainer.isVisible = state.subjects.isEmpty()
                    scrollState?.let { currentBinding.mainRecyclerView.layoutManager?.onRestoreInstanceState(it) }
                    scrollState = null
                }
                renderEmptyText()
            }
            is RecordbookUiState.Error -> {
                renderPeriod(state.programs, state.selection)
                binding.swipeRefreshLayout.isVisible = false
                binding.stateContainer.isVisible = true
                binding.stateIcon.setImageResource(R.drawable.ic_error_rounded)
                binding.stateTitle.setText(R.string.common_load_error_title)
                binding.stateDescription.setText(state.error.messageRes())
                binding.stateAction.isVisible = true
            }
            RecordbookUiState.Empty -> {
                renderPeriod(emptyList(), null)
                binding.swipeRefreshLayout.isVisible = false
                binding.stateContainer.isVisible = true
                renderEmptyText()
            }
        }
    }

    private fun renderEmptyText() {
        binding.stateIcon.setImageResource(R.drawable.ic_menu_book)
        binding.stateTitle.setText(R.string.recordbook_empty_title)
        binding.stateDescription.setText(R.string.recordbook_empty_description)
        binding.stateAction.isVisible = true
    }

    private fun renderPeriod(programs: List<RecordbookProgram>, selected: RecordbookSelection?) {
        if (selection != null && selected != selection) {
            scrollState = null
            binding.mainRecyclerView.scrollToPosition(0)
        }
        selection = selected
        binding.periodButton.isEnabled = selected != null
        binding.periodButton.text = selected?.let { getString(R.string.recordbook_period_value, it.period.course, it.period.semester) }
            ?: getString(R.string.recordbook_period_title)
        binding.periodYear.text = selected?.period?.studyYear ?: " "
        binding.periodButton.setOnClickListener {
            selected?.let { RecordbookPeriodBottomSheet.show(parentFragmentManager, programs, it) }
        }
    }

    private fun openSubject(subject: RecordbookSubject) {
        val selected = selection ?: return
        val arguments = bundleOf(
            RecordbookSubjectViewModel.ARG_ENTRY_ID to subject.entryId,
            RecordbookSubjectViewModel.ARG_PROGRAM_ID to selected.program.id,
            RecordbookSubjectViewModel.ARG_SEMESTER to selected.period.semester,
            RecordbookSubjectViewModel.ARG_STUDY_YEAR to selected.period.studyYear
        )
        subject.barsJournal?.let {
            arguments.putLong(RecordbookSubjectViewModel.ARG_BARS_PLAN, it.planId)
            arguments.putString(RecordbookSubjectViewModel.ARG_BARS_TYPE, it.type)
            arguments.putString(RecordbookSubjectViewModel.ARG_BARS_IDENTIFIER, it.identifier)
        }
        openScreen(AppScreen.RECORDBOOK_SUBJECT, arguments)
    }
}
