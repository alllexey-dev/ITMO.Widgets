package dev.alllexey.itmowidgets.feature.resources.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkActions
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkEditor
import dev.alllexey.itmowidgets.core.ui.openLink
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * All links of one subject period, drawn by `SubjectLinksSheetRoute` of `:shared:feature-resources`: sections by
 * category, chats, links of past years and the add button. The Fragment keeps the stable entry points (class name,
 * [TAG], [newInstance]), opens as tall as its content, and performs the effects: opening a link, the editor and the
 * actions sheet, and the snackbar of a failure.
 */
@AndroidEntryPoint
class SubjectLinksBottomSheet : ItmoBottomSheetFragment() {
    private val viewModel: SubjectLinksViewModel by viewModel()

    @Composable
    override fun SheetContent() {
        val args = viewModel.scope.toArgs()
        SubjectLinksSheetRoute(
            viewModel = viewModel,
            onOpen = { openLink(it.url, requireView()) },
            onLinkActions = { openLinkActions(args, it.id) },
            onAdd = { openLinkEditor(args) },
            onClose = ::onCloseRequest,
            onFailure = ::showFailure,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    private fun showFailure(error: AppError) {
        Snackbar.make(requireView(), error.messageRes(), Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "SubjectLinksBottomSheet"

        fun newInstance(args: SubjectLinksArgs) = SubjectLinksBottomSheet().apply { arguments = args.toArguments() }
    }
}
