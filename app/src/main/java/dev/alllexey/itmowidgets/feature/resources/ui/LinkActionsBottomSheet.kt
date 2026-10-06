package dev.alllexey.itmowidgets.feature.resources.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.ui.copyToClipboard
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkEditor
import dev.alllexey.itmowidgets.core.ui.navigation.openSheetScores
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.core.ui.bind
import dev.alllexey.itmowidgets.core.ui.displayTitle
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.databinding.SheetLinkActionsBinding
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import dev.alllexey.itmowidgets.core.ui.openLink
import dev.alllexey.itmowidgets.core.ui.expandToContent
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * What can be done with one link. Own: open, pin, edit, delete, with the review state and the reason
 * of a rejection. A Google Sheet of any author also offers «Мои баллы», the own total from it. Others': the vote pill, open, the author's profile, pin, report. Actions that need the server are
 * absent without it. A vote keeps the sheet open; every other action closes it on success.
 */
@AndroidEntryPoint
class LinkActionsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: SheetLinkActionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubjectLinksViewModel by viewModel()
    private val linkId: String by lazy { checkNotNull(requireArguments().getString(SubjectLinksArgs.LINK_ID)) }

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
                is LinkEvent.Failed -> Snackbar.make(binding.root, event.error.messageRes(), Snackbar.LENGTH_SHORT).show()
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun copyLink(url: String) {
        val copied = requireContext().copyToClipboard(getString(R.string.links_copy), url) {
            Toast.makeText(requireContext(), R.string.links_copied, Toast.LENGTH_SHORT).show()
        }
        // The sheet closes whether or not the system confirmed the copy itself.
        if (copied) dismiss()
    }

    private fun render(state: SubjectLinksUiState) = with(binding) {
        val snapshot = state.content ?: return@with
        val link = (snapshot.mine + snapshot.shared + snapshot.previous).firstOrNull { it.id == linkId }
        if (link == null) {
            // Deleted here or elsewhere; nothing is left to act on.
            if (!state.busy) dismiss()
            return@with
        }
        val pinned = snapshot.pinnedId == link.id
        val previous = snapshot.previous.any { it.id == link.id }
        title.text = link.displayTitle()
        meta.text = requireContext().linkMeta(link, pinned, previous)
        bindVotes(link, state.canVote)
        val note = link.reviewNote.takeIf {
            link.isMine && (link.status == SubjectLinkStatus.REJECTED || link.status == SubjectLinkStatus.HIDDEN)
        }
        reviewNote.isVisible = note != null
        reviewNote.text = note?.let { getString(R.string.links_rejected_reason, it) }

        val online = snapshot.servicesEnabled
        actionOpen.setOnClickListener { openLink(link.url, root); dismiss() }
        actionCopy.setOnClickListener { copyLink(link.url) }
        actionScores.isVisible = GoogleSheetUrl.parse(link.url) != null
        actionScores.setOnClickListener { openScores(link) }
        actionPin.isVisible = link.isMine || online
        actionPin.setText(if (pinned) R.string.links_unpin else R.string.links_pin)
        actionPin.setCompoundDrawablesRelativeWithIntrinsicBounds(if (pinned) R.drawable.ic_keep_off else R.drawable.ic_keep, 0, 0, 0)
        actionPin.setOnClickListener { if (!busy()) viewModel.pin(link.id) }
        actionEdit.isVisible = link.isMine
        actionEdit.setOnClickListener { edit(link) }
        actionDelete.isVisible = link.isMine
        actionDelete.setOnClickListener { confirmDelete(link) }
        actionReport.isVisible = !link.isMine && state.canReport && !link.reportedByMe
        actionReport.setOnClickListener { report(link) }
        // A deleted account's shared links move to a placeholder author with a negative ISU and no profile.
        val author = link.author?.takeIf { !link.isMine && it.isu > 0 }
        actionAuthor.isVisible = author != null
        author?.let { person ->
            actionAuthor.text = getString(R.string.links_author, person.name)
            actionAuthor.setOnClickListener {
                dismiss()
                openUserProfile(person.isu)
            }
        }
    }

    /** Others' links vote as in the list and the score follows the repository; an own private link has no score. */
    private fun bindVotes(link: SubjectLink, canVote: Boolean) = with(binding) {
        val visible = !link.isMine || link.visibility != LinkVisibility.PRIVATE
        votePill.root.isVisible = visible
        if (visible) votePill.bind(link, canVote = !link.isMine && canVote) { up -> if (!busy()) viewModel.vote(link.id, up) }
        heading.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginEnd = if (visible) 0 else resources.getDimensionPixelSize(R.dimen.design_screen_margin)
        }
    }

    /** A sent action closes the sheet on success; until then, or until it fails, the rows ignore taps. */
    private fun busy(): Boolean = viewModel.uiState.value.busy

    private fun edit(link: SubjectLink) {
        if (busy()) return
        openLinkEditor(viewModel.scope.toArgs(), link.id)
        dismiss()
    }

    private fun openScores(link: SubjectLink) {
        if (busy()) return
        val scope = viewModel.scope
        openSheetScores(SheetScoresArgs(scope.subjectId, scope.subjectName, scope.periodKey, link.url, SheetScoresArgs.Step.CONNECT))
        dismiss()
    }

    private fun confirmDelete(link: SubjectLink) {
        if (busy()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.links_delete_confirm)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.links_delete) { _, _ -> viewModel.delete(link.id) }
            .show()
    }

    private fun report(link: SubjectLink) {
        if (busy()) return
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
