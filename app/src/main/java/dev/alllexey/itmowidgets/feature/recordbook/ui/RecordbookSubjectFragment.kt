package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.flow.combine
import dev.alllexey.itmowidgets.feature.recordbook.presentation.hasScheduleContent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTab
import com.google.android.material.tabs.TabLayout
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.applyAppRefreshColors
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
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
    private val barsLogin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.refresh()
    }
    private var lastRefreshError: AppError? = null
    private var lastBarsError: AppError? = null
    private var errorSnackbar: Snackbar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordbookSubjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RecordbookControlAdapter(viewModel::refresh, SubjectHubActions(
            onConfirmBinding = viewModel::confirmBinding,
            onRejectProposal = viewModel::rejectProposal,
            onRetryLessons = viewModel::retryLessons,
            onOpenResource = { openResource(it.url) }
        ))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        binding.swipeRefreshLayout.applyAppRefreshColors()
        binding.backButton.setOnClickListener { closeScreen() }
        binding.sourceButton.setOnClickListener { showRecordbookSourceInfo(requireContext()) }
        binding.swipeRefreshLayout.setOnRefreshListener(viewModel::refresh)
        binding.stateAction.setOnClickListener { viewModel.refresh() }
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(if (tab.position == 0) SubjectTab.SCORES else SubjectTab.SCHEDULE)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        combine(viewModel.uiState, viewModel.tab) { state, tab -> state to tab }
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { (state, tab) -> render(state, tab) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        errorSnackbar?.dismiss()
        errorSnackbar = null
        lastRefreshError = null
        lastBarsError = null
        _binding = null
        super.onDestroyView()
    }

    private fun openResource(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun render(state: RecordbookSubjectUiState, tab: SubjectTab) {
        if (state !is RecordbookSubjectUiState.Content) binding.loading.isVisible = state is RecordbookSubjectUiState.Loading
        binding.swipeRefreshLayout.isRefreshing = (state as? RecordbookSubjectUiState.Content)?.refreshing == true
        val refreshError = (state as? RecordbookSubjectUiState.Content)?.refreshError
        val barsError = (state as? RecordbookSubjectUiState.Content)?.barsError
        if (refreshError != lastRefreshError || barsError != lastBarsError) {
            errorSnackbar?.dismiss()
            errorSnackbar = refreshError?.let {
                Snackbar.make(binding.root, getString(R.string.recordbook_refresh_error, getString(it.messageRes())), Snackbar.LENGTH_LONG)
                    .setAction(R.string.common_retry) { viewModel.refresh() }.also(Snackbar::show)
            } ?: barsError?.let { error ->
                recordbookBarsSnackbar(binding.root, error, viewModel::refresh) {
                    barsLogin.launch(Intent(requireContext(), BarsLoginActivity::class.java))
                }
            }
            lastRefreshError = refreshError
            lastBarsError = barsError
        }
        when (state) {
            RecordbookSubjectUiState.Loading -> {
                binding.swipeRefreshLayout.isVisible = false
                binding.stateContainer.isVisible = false
            }
            is RecordbookSubjectUiState.Content -> {
                val currentBinding = binding
                // The schedule tab appears once the hub has anything to show; a selection is kept across reloads.
                val tabsVisible = state.hub.hasScheduleContent
                binding.tabs.isVisible = tabsVisible
                val shownTab = if (tabsVisible) tab else SubjectTab.SCORES
                val position = if (shownTab == SubjectTab.SCORES) 0 else 1
                if (binding.tabs.selectedTabPosition != position) binding.tabs.getTabAt(position)?.select()
                adapter.submitContent(state, shownTab) {
                    if (_binding !== currentBinding || viewModel.uiState.value != state) return@submitContent
                    currentBinding.loading.isVisible = false
                    currentBinding.stateContainer.isVisible = false
                    currentBinding.swipeRefreshLayout.isVisible = true
                }
            }
            is RecordbookSubjectUiState.Error -> {
                binding.swipeRefreshLayout.isVisible = false
                binding.stateContainer.isVisible = true
                binding.stateIcon.setImageResource(R.drawable.ic_error_rounded)
                binding.stateTitle.setText(R.string.common_load_error_title)
                binding.stateDescription.setText(state.error.messageRes())
                binding.stateAction.isVisible = true
            }
        }
    }
}
