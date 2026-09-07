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
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentRecordbookSubjectBinding
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class RecordbookSubjectFragment : Fragment() {
    private var _binding: FragmentRecordbookSubjectBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecordbookSubjectViewModel by viewModels()
    private lateinit var adapter: RecordbookControlAdapter
    private var lastRefreshError: AppError? = null
    private var errorSnackbar: Snackbar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordbookSubjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RecordbookControlAdapter(viewModel::refresh)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        binding.swipeRefreshLayout.setColorSchemeColors(requireContext().color.primary)
        binding.backButton.setOnClickListener { closeScreen() }
        binding.sourceButton.setOnClickListener { showRecordbookSourceInfo(requireContext()) }
        binding.swipeRefreshLayout.setOnRefreshListener(viewModel::refresh)
        binding.stateAction.setOnClickListener { viewModel.refresh() }
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        errorSnackbar?.dismiss()
        errorSnackbar = null
        lastRefreshError = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: RecordbookSubjectUiState) {
        if (state !is RecordbookSubjectUiState.Content) binding.loading.isVisible = state is RecordbookSubjectUiState.Loading
        binding.swipeRefreshLayout.isRefreshing = (state as? RecordbookSubjectUiState.Content)?.refreshing == true
        val refreshError = (state as? RecordbookSubjectUiState.Content)?.refreshError
        if (refreshError != lastRefreshError) {
            errorSnackbar?.dismiss()
            errorSnackbar = refreshError?.let {
                Snackbar.make(binding.root, getString(R.string.recordbook_refresh_error, getString(it.messageRes())), Snackbar.LENGTH_LONG)
                    .setAction(R.string.common_retry) { viewModel.refresh() }.also(Snackbar::show)
            }
            lastRefreshError = refreshError
        }
        when (state) {
            RecordbookSubjectUiState.Loading -> {
                binding.swipeRefreshLayout.isVisible = false
                binding.stateContainer.isVisible = false
            }
            is RecordbookSubjectUiState.Content -> {
                val currentBinding = binding
                adapter.submitContent(state) {
                    if (_binding !== currentBinding || viewModel.uiState.value != state) return@submitContent
                    currentBinding.loading.isVisible = false
                    currentBinding.stateContainer.isVisible = false
                    currentBinding.swipeRefreshLayout.isVisible = true
                }
            }
            is RecordbookSubjectUiState.Error -> {
                binding.swipeRefreshLayout.isVisible = false
                binding.stateContainer.isVisible = true
                binding.stateTitle.setText(R.string.common_load_error_title)
                binding.stateDescription.setText(state.error.messageRes())
                binding.stateAction.isVisible = true
            }
        }
    }
}
