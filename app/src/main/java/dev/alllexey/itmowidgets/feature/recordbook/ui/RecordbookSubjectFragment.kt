package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.applyAppRefreshColors
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkActions
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkEditor
import dev.alllexey.itmowidgets.core.ui.navigation.openSubjectLinks
import dev.alllexey.itmowidgets.core.ui.openLink
import dev.alllexey.itmowidgets.databinding.FragmentRecordbookSubjectBinding
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** One page per subject: the result, links, chats, scores, teachers and the nearest lessons. */
@AndroidEntryPoint
class RecordbookSubjectFragment : Fragment() {
    private var _binding: FragmentRecordbookSubjectBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecordbookSubjectViewModel by viewModels()
    private lateinit var adapter: SubjectHubAdapter
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
        binding.toolbar.setNavigationOnClickListener { closeScreen() }
        ViewCompat.setAccessibilityHeading(binding.title, true)
        binding.stateAction.setOnClickListener { viewModel.refresh() }
        adapter = SubjectHubAdapter({ viewModel.refresh() }, SubjectHubActions(
            onConfirmBinding = viewModel::confirmBinding,
            onRejectProposal = viewModel::rejectProposal,
            onRetryLessons = viewModel::retryLessons,
            onShowAllLessons = viewModel::showAllLessons,
            onOpenLink = { openLink(it, binding.root) },
            onLinkActions = { link -> linksArgs()?.let { openLinkActions(it, link.id) } },
            onAllLinks = { linksArgs()?.let(::openSubjectLinks) },
            onAddLink = { linksArgs()?.let { openLinkEditor(it) } }
        ))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        binding.swipeRefreshLayout.applyAppRefreshColors()
        binding.swipeRefreshLayout.setOnRefreshListener({ viewModel.refresh() })
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
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

    private fun linksArgs(): SubjectLinksArgs? =
        (viewModel.uiState.value as? RecordbookSubjectUiState.Content)?.hub?.resourceScope
            ?.let { SubjectLinksArgs(it.subjectId, it.subjectName, it.periodKey) }

    private fun render(state: RecordbookSubjectUiState) {
        if (state !is RecordbookSubjectUiState.Content) binding.loading.isVisible = state is RecordbookSubjectUiState.Loading
        val refreshError = (state as? RecordbookSubjectUiState.Content)?.refreshError
        val barsError = (state as? RecordbookSubjectUiState.Content)?.barsError
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
        val semester = requireArguments().getInt(RecordbookSubjectViewModel.ARG_SEMESTER)
        when (state) {
            RecordbookSubjectUiState.Loading -> {
                binding.title.text = null
                binding.subtitle.text = getString(R.string.subject_semester, semester)
                binding.swipeRefreshLayout.isVisible = false
                binding.stateContainer.isVisible = false
            }
            is RecordbookSubjectUiState.Content -> {
                binding.title.text = state.subject.name
                binding.subtitle.text = state.subject.controlType.takeIf(String::isNotBlank)
                    ?.let { getString(R.string.subject_subtitle, it, semester) } ?: getString(R.string.subject_semester, semester)
                binding.swipeRefreshLayout.isRefreshing = state.refreshing
                val currentBinding = binding
                adapter.submitContent(state) {
                    if (_binding !== currentBinding) return@submitContent
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
