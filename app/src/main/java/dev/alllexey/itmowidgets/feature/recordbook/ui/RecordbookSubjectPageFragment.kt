package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.ui.applyAppRefreshColors
import dev.alllexey.itmowidgets.core.ui.navigation.openSubjectLinks
import dev.alllexey.itmowidgets.databinding.FragmentRecordbookSubjectPageBinding
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTab
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** One tab of the subject screen; the list for its tab, fed by the parent screen's view model. */
@AndroidEntryPoint
class RecordbookSubjectPageFragment : Fragment() {
    private var _binding: FragmentRecordbookSubjectPageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecordbookSubjectViewModel by viewModels(ownerProducer = { requireParentFragment() })
    private lateinit var adapter: RecordbookControlAdapter
    private val tab: SubjectTab by lazy { SubjectTab.valueOf(requireNotNull(requireArguments().getString(ARG_TAB))) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordbookSubjectPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RecordbookControlAdapter({ viewModel.refresh() }, SubjectHubActions(
            onConfirmBinding = viewModel::confirmBinding,
            onRejectProposal = viewModel::rejectProposal,
            onRetryLessons = viewModel::retryLessons,
            onOpenResource = { openResource(it.url) },
            onOpenResources = { scope -> openSubjectLinks(SubjectLinksArgs(scope.subjectId, scope.subjectName, scope.periodKey)) }
        ))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        binding.swipeRefreshLayout.applyAppRefreshColors()
        binding.swipeRefreshLayout.setOnRefreshListener({ viewModel.refresh() })
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(state: RecordbookSubjectUiState) {
        val content = state as? RecordbookSubjectUiState.Content ?: return
        binding.swipeRefreshLayout.isRefreshing = content.refreshing
        adapter.submitContent(content, tab)
    }

    private fun openResource(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TAB = "subject_tab"

        fun newInstance(tab: SubjectTab): RecordbookSubjectPageFragment = RecordbookSubjectPageFragment().apply {
            arguments = bundleOf(ARG_TAB to tab.name)
        }
    }
}
