package dev.alllexey.itmowidgets.feature.resources.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkActions
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkEditor
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.toUiText
import dev.alllexey.itmowidgets.databinding.SheetSubjectLinksBinding
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import dev.alllexey.itmowidgets.core.ui.openLink

/** All links of one subject period: sections by category, chats, links of past years and «Добавить ссылку». */
@AndroidEntryPoint
class SubjectLinksBottomSheet : BottomSheetDialogFragment() {
    private var _binding: SheetSubjectLinksBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubjectLinksViewModel by viewModels()
    private lateinit var adapter: SubjectLinksAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetSubjectLinksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        expandToContent()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = viewModel.scope.toArgs()
        binding.subject.text = args.subjectName
        binding.closeButton.setOnClickListener { dismiss() }
        binding.addButton.setOnClickListener { openLinkEditor(args) }
        adapter = SubjectLinksAdapter(
            onOpen = { openLink(it.url, binding.root) },
            onActions = { openLinkActions(args, it.id) },
            onVote = { link, up -> viewModel.vote(link.id, up) },
            onToggleSaved = { viewModel.toggleSaved(it.id) },
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        binding.state.stateAction.setText(R.string.common_retry)
        binding.state.stateAction.setOnClickListener { viewModel.refresh() }
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { event ->
            if (event is LinkEvent.Failed) Snackbar.make(binding.root, event.text.resolve(requireContext()), Snackbar.LENGTH_SHORT).show()
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(state: SubjectLinksUiState) = with(binding) {
        val content = state.content
        if (content == null) {
            recyclerView.isVisible = false
            loading.isVisible = state.error == null
            showState(state.error != null, R.drawable.ic_error_rounded, getString(R.string.links_error_title),
                state.error?.toUiText()?.resolve(requireContext()), retry = true)
            return
        }
        val rows = state.linkRows()
        adapter.submitList(rows) {
            val current = _binding ?: return@submitList
            current.loading.isVisible = false
            current.recyclerView.isVisible = rows.isNotEmpty()
            showState(rows.isEmpty(), R.drawable.ic_link, getString(R.string.links_empty), null, retry = false)
        }
    }

    private fun showState(visible: Boolean, icon: Int, title: String, description: String?, retry: Boolean) = with(binding.state) {
        root.isVisible = visible
        if (!visible) return@with
        stateIcon.setImageResource(icon)
        stateTitle.text = title
        stateDescription.text = description
        stateDescription.isVisible = description != null
        stateAction.isVisible = retry
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "SubjectLinksBottomSheet"

        fun newInstance(args: SubjectLinksArgs) = SubjectLinksBottomSheet().apply { arguments = args.toArguments() }
    }
}
