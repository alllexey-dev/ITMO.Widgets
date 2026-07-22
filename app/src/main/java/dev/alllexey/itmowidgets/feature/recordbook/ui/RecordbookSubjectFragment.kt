package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentRecordbookSubjectBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.NumberFormat

@AndroidEntryPoint
class RecordbookSubjectFragment : Fragment() {

    private var _binding: FragmentRecordbookSubjectBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordbookSubjectViewModel by viewModels()
    private lateinit var adapter: RecordbookControlAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordbookSubjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RecordbookControlAdapter()
        binding.recyclerView.adapter = adapter
        setupHeader()
        setupListeners()
        observeState()
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun setupHeader() {
        val args = requireArguments()
        val score = args.getDouble(ARG_SCORE).takeUnless(Double::isNaN)
        binding.subjectName.text = args.getString(ARG_NAME)?.trim()
        binding.status.text = args.getString(ARG_RATE)
        binding.score.text = score?.let {
            getString(R.string.recordbook_score_value, formatNumber(it))
        } ?: getString(R.string.recordbook_no_score)
        binding.progress.progress = score?.toInt()?.coerceIn(0, 100) ?: 0
        binding.progress.trackStopIndicatorSize = 0

        val colors = requireContext().color
        val isAttention = args.getString(ARG_RATE)?.startsWith("2") == true
        val statusColor = if (isAttention) colors.error else colors.onSurface
        binding.status.setTextColor(statusColor)
        binding.progress.setIndicatorColor(statusColor)

        val meta = buildList {
            args.getString(ARG_CONTROL_TYPE)
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
            args.getString(ARG_TEACHER)
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
        }
        binding.meta.text = meta.joinToString(" · ")
        binding.meta.isVisible = meta.isNotEmpty()

        binding.swipeRefreshLayout.setColorSchemeColors(colors.primary)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(colors.background)
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.swipeRefreshLayout.setOnRefreshListener(viewModel::refresh)
        binding.stateAction.setOnClickListener { viewModel.refresh() }
    }

    private fun observeState() {
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                when (state) {
                    RecordbookSubjectUiState.Loading -> {
                        binding.swipeRefreshLayout.isVisible = true
                        binding.swipeRefreshLayout.isRefreshing = true
                        binding.stateContainer.isVisible = false
                    }
                    is RecordbookSubjectUiState.Success -> {
                        binding.swipeRefreshLayout.isRefreshing = false
                        if (state.controls.isEmpty()) {
                            binding.swipeRefreshLayout.isVisible = false
                            binding.stateContainer.isVisible = true
                            binding.stateTitle.setText(R.string.recordbook_details_empty_title)
                            binding.stateDescription.setText(
                                R.string.recordbook_details_empty_description
                            )
                            binding.stateAction.isVisible = false
                        } else {
                            binding.swipeRefreshLayout.isVisible = true
                            binding.stateContainer.isVisible = false
                            adapter.submitControls(state.controls)
                        }
                    }
                    is RecordbookSubjectUiState.Error -> {
                        binding.swipeRefreshLayout.isVisible = false
                        binding.swipeRefreshLayout.isRefreshing = false
                        binding.stateContainer.isVisible = true
                        binding.stateTitle.setText(R.string.common_load_error_title)
                        binding.stateDescription.text = state.message
                        binding.stateAction.isVisible = true
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    companion object {
        const val ARG_NAME = "subject_name"
        const val ARG_CONTROL_TYPE = "control_type"
        const val ARG_SCORE = "subject_score"
        const val ARG_RATE = "subject_rate"
        const val ARG_TEACHER = "subject_teacher"

        private fun formatNumber(value: Double): String = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
        }.format(value)
    }
}
