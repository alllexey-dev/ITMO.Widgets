package dev.alllexey.itmowidgets.feature.recordbook.ui

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
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentRecordbookBinding
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookSubject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class RecordbookFragment : Fragment() {

    private var _binding: FragmentRecordbookBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordbookViewModel by viewModels()
    private lateinit var adapter: RecordbookAdapter
    private var lastState: RecordbookUiState.Success? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordbookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecycler()
        setupListeners()
        setupPeriodResult()
        observeState()
        viewModel.ensureDataLoaded()
    }

    override fun onDestroyView() {
        binding.mainRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun setupRecycler() {
        adapter = RecordbookAdapter(::openSubject)
        binding.mainRecyclerView.adapter = adapter
        val colors = requireContext().color
        binding.swipeRefreshLayout.setColorSchemeColors(colors.primary)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(colors.background)
    }

    private fun setupListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener(viewModel::refresh)
        binding.periodButton.setOnClickListener {
            lastState?.let { state ->
                RecordbookPeriodBottomSheet.show(
                    parentFragmentManager,
                    state.programs,
                    state.selection
                )
            }
        }
        binding.filterButton.setOnClickListener { showFilterDialog() }
        binding.stateAction.setOnClickListener { viewModel.refresh() }
    }

    private fun setupPeriodResult() {
        parentFragmentManager.setFragmentResultListener(
            RecordbookPeriodBottomSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, result ->
            viewModel.selectPeriod(
                result.getLong(RecordbookPeriodBottomSheet.RESULT_PROGRAM_ID),
                result.getInt(RecordbookPeriodBottomSheet.RESULT_SEMESTER)
            )
        }
    }

    private fun observeState() {
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                when (state) {
                    RecordbookUiState.Loading -> renderLoading()
                    is RecordbookUiState.Success -> renderSuccess(state)
                    is RecordbookUiState.Error -> renderError(state.message)
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun renderLoading() {
        binding.swipeRefreshLayout.isVisible = true
        binding.swipeRefreshLayout.isRefreshing = true
        binding.stateContainer.isVisible = false
    }

    private fun renderSuccess(state: RecordbookUiState.Success) {
        lastState = state
        binding.swipeRefreshLayout.isRefreshing = false
        binding.periodButton.text = getString(
            R.string.recordbook_period_button,
            state.selection.period.course,
            state.selection.period.semesterInCourse,
            state.selection.period.studyYear
        )
        binding.filterButton.isActivated = state.filter != RecordbookFilter.ALL
        if (state.subjects.isEmpty()) {
            adapter.submitData(state.allSubjects, emptyList())
            binding.swipeRefreshLayout.isVisible = false
            binding.stateContainer.isVisible = true
            binding.stateIcon.setImageResource(R.drawable.ic_menu_book)
            binding.stateTitle.setText(R.string.recordbook_empty_title)
            binding.stateDescription.setText(R.string.recordbook_empty_description)
            binding.stateAction.isVisible = false
        } else {
            binding.swipeRefreshLayout.isVisible = true
            binding.stateContainer.isVisible = false
            adapter.submitData(state.allSubjects, state.subjects)
        }
    }

    private fun renderError(message: String) {
        binding.swipeRefreshLayout.isVisible = false
        binding.swipeRefreshLayout.isRefreshing = false
        binding.stateContainer.isVisible = true
        binding.stateIcon.setImageResource(R.drawable.ic_error)
        binding.stateTitle.setText(R.string.common_load_error_title)
        binding.stateDescription.text = message
        binding.stateAction.isVisible = true
    }

    private fun showFilterDialog() {
        val current = lastState?.filter ?: RecordbookFilter.ALL
        val filters = RecordbookFilter.entries
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.recordbook_filter)
            .setSingleChoiceItems(
                resources.getStringArray(R.array.recordbook_filter_options),
                filters.indexOf(current)
            ) { dialog, position ->
                viewModel.setFilter(filters[position])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.common_close, null)
            .show()
    }

    private fun openSubject(subject: RecordbookSubject) {
        findNavController().navigate(
            R.id.action_recordbook_to_recordbook_subject,
            bundleOf(
                RecordbookSubjectViewModel.ARG_ENTRY_ID to subject.entryId,
                RecordbookSubjectFragment.ARG_NAME to subject.name,
                RecordbookSubjectFragment.ARG_CONTROL_TYPE to subject.controlType,
                RecordbookSubjectFragment.ARG_SCORE to (subject.score ?: Double.NaN),
                RecordbookSubjectFragment.ARG_RATE to subject.displayRate,
                RecordbookSubjectFragment.ARG_TEACHER to subject.teacherName.orEmpty()
            )
        )
    }
}
