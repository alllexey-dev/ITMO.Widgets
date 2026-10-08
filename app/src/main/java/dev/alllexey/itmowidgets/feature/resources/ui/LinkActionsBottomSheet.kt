package dev.alllexey.itmowidgets.feature.resources.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.copyToClipboard
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkEditor
import dev.alllexey.itmowidgets.core.ui.navigation.openSheetScores
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.core.ui.openLink
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * What can be done with one link, drawn by `LinkActionsSheetRoute` of `:shared:feature-resources`: the vote pill,
 * open, the author's profile, copy, `Мои баллы`, pin, edit, delete and report. The Fragment keeps the stable entry
 * points (class name, [TAG], [newInstance]), opens as tall as its content, and performs the effects: opening the link,
 * the clipboard, the profile, the sheet scores, the editor and the report dialog, closing, and the snackbar of a
 * failure. A vote keeps the sheet open; every other action closes it.
 */
@AndroidEntryPoint
class LinkActionsBottomSheet : ItmoBottomSheetFragment() {
    private val viewModel: SubjectLinksViewModel by viewModel()
    private val linkId: String by lazy { checkNotNull(requireArguments().getString(SubjectLinksArgs.LINK_ID)) }

    @Composable
    override fun SheetContent() {
        val effects = remember {
            LinkActionsActions(
                onOpen = { link ->
                    openLink(link.url, requireView())
                    dismiss()
                },
                onAuthor = { isu ->
                    dismiss()
                    openUserProfile(isu)
                },
                onCopy = { copyLink(it.url) },
                onScores = ::openScores,
                onEdit = ::edit,
                onReport = ::report,
            )
        }
        LinkActionsSheetRoute(
            viewModel = viewModel,
            linkId = linkId,
            effects = effects,
            onDismiss = ::dismiss,
            onFailure = ::showFailure,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    private fun copyLink(url: String) {
        val copied = requireContext().copySubjectLink(url)
        // The sheet closes whether or not the system confirmed the copy itself.
        if (copied) dismiss()
    }

    private fun edit(link: SubjectLink) {
        openLinkEditor(viewModel.scope.toArgs(), link.id)
        dismiss()
    }

    private fun openScores(link: SubjectLink) {
        val scope = viewModel.scope
        openSheetScores(SheetScoresArgs(scope.subjectId, scope.subjectName, scope.periodKey, link.url, SheetScoresArgs.Step.CONNECT))
        dismiss()
    }

    private fun report(link: SubjectLink) {
        ReportLinkDialogFragment.newInstance(viewModel.scope.toArgs(), link.id)
            .show(parentFragmentManager, ReportLinkDialogFragment.TAG)
        dismiss()
    }

    private fun showFailure(error: AppError) {
        Snackbar.make(requireView(), error.messageRes(), Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "LinkActionsBottomSheet"

        fun newInstance(args: SubjectLinksArgs, linkId: String) =
            LinkActionsBottomSheet().apply { arguments = args.toArguments(linkId) }
    }
}

/** Copies a link's [url]; below Android 13 a toast confirms it. False when the device has no clipboard. */
internal fun Context.copySubjectLink(url: String): Boolean =
    copyToClipboard(getString(R.string.links_copy), url) {
        Toast.makeText(this, R.string.links_copied, Toast.LENGTH_SHORT).show()
    }
