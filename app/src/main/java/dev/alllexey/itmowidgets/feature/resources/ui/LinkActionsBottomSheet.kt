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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkEditor
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.databinding.SheetLinkActionsBinding
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import dev.alllexey.itmowidgets.core.ui.openLink

/**
 * What can be done with one link. Own: open, pin, edit, delete, with the review state and the reason
 * of a rejection. Others': open, add to own, pin, report. Actions that need the server are absent without it.
 */
@AndroidEntryPoint
class LinkActionsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: SheetLinkActionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubjectLinksViewModel by viewModels()
    private val linkId: String by lazy { checkNotNull(requireArguments().getString(SubjectLinksArgs.LINK_ID)) }
    /** A sent action closes the sheet on success; until then the rows ignore taps. */
    private var pending = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetLinkActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        expandToContent()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { event ->
            when (event) {
                LinkEvent.Done, LinkEvent.Saved -> dismiss()
                is LinkEvent.Failed -> {
                    pending = false
                    Snackbar.make(binding.root, event.text.resolve(requireContext()), Snackbar.LENGTH_SHORT).show()
                }
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(state: SubjectLinksUiState) = with(binding) {
        val snapshot = state.content ?: return@with
        val link = (snapshot.mine + snapshot.shared + snapshot.previous).firstOrNull { it.id == linkId }
        if (link == null) {
            // Deleted here or elsewhere; nothing is left to act on.
            if (!pending) dismiss()
            return@with
        }
        val pinned = snapshot.pinnedId == link.id
        val previous = snapshot.previous.any { it.id == link.id }
        title.text = link.displayTitle()
        meta.text = requireContext().linkMeta(link, pinned, previous)
        val note = link.reviewNote.takeIf {
            link.isMine && (link.status == SubjectLinkStatus.REJECTED || link.status == SubjectLinkStatus.HIDDEN)
        }
        reviewNote.isVisible = note != null
        reviewNote.text = note?.let { getString(R.string.links_rejected_reason, it) }

        val online = snapshot.servicesEnabled
        actionOpen.setOnClickListener { openLink(link.url, root); dismiss() }
        actionSave.isVisible = !link.isMine && online
        actionSave.setText(if (link.isSaved) R.string.links_unsave_own else R.string.links_save_own)
        actionSave.setCompoundDrawablesRelativeWithIntrinsicBounds(if (link.isSaved) R.drawable.ic_remove else R.drawable.ic_add, 0, 0, 0)
        actionSave.setOnClickListener { send { viewModel.toggleSaved(link.id) } }
        actionPin.isVisible = link.isMine || online
        actionPin.setText(if (pinned) R.string.links_unpin else R.string.links_pin)
        actionPin.setCompoundDrawablesRelativeWithIntrinsicBounds(if (pinned) R.drawable.ic_keep_off else R.drawable.ic_keep, 0, 0, 0)
        actionPin.setOnClickListener { send { viewModel.pin(link.id) } }
        actionEdit.isVisible = link.isMine
        actionEdit.setOnClickListener { edit(link) }
        actionDelete.isVisible = link.isMine
        actionDelete.setOnClickListener { confirmDelete(link) }
        actionReport.isVisible = !link.isMine && state.canReport && !link.reportedByMe
        actionReport.setOnClickListener { report(link) }
    }

    private fun send(action: () -> Unit) {
        if (pending) return
        pending = true
        action()
    }

    private fun edit(link: SubjectLink) {
        if (pending) return
        openLinkEditor(viewModel.scope.toArgs(), link.id)
        dismiss()
    }

    private fun confirmDelete(link: SubjectLink) {
        if (pending) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.links_delete_confirm)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.links_delete) { _, _ -> send { viewModel.delete(link.id) } }
            .show()
    }

    private fun report(link: SubjectLink) {
        if (pending) return
        ReportLinkDialogFragment.newInstance(viewModel.scope.toArgs(), link.id).show(parentFragmentManager, ReportLinkDialogFragment.TAG)
        dismiss()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "LinkActionsBottomSheet"

        fun newInstance(args: SubjectLinksArgs, linkId: String) =
            LinkActionsBottomSheet().apply { arguments = args.toArguments(linkId) }
    }
}
