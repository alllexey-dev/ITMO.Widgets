package dev.alllexey.itmowidgets.feature.resources.ui

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.clipboardText
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.url.HttpsNavigationPolicy
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.designsystem.host.SheetSpec
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Adds a link or edits an own one, drawn by `LinkEditorSheetRoute` of `:shared:feature-resources`: address, category
 * chips, optional title and who sees it. The Fragment keeps the stable entry points (class name, [TAG],
 * [newInstance]), opens as tall as its content with the window resized for the keyboard, and performs the effects:
 * the clipboard link a new sheet starts with, closing once the link is saved and the snackbar of a failed save.
 */
@AndroidEntryPoint
class LinkEditorBottomSheet : ItmoBottomSheetFragment() {
    private val viewModel: LinkEditorViewModel by viewModel()

    override val spec: SheetSpec get() = SPEC

    @Composable
    override fun SheetContent() {
        LinkEditorSheetRoute(
            viewModel = viewModel,
            onDone = ::dismiss,
            onFailure = ::showFailure,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null && !viewModel.uiState.value.editing) pasteOnFirstFocus(view)
    }

    private fun showFailure(error: AppError) {
        Snackbar.make(requireView(), error.messageRes(), Snackbar.LENGTH_SHORT).show()
    }

    /** Clipboard reads need window focus, which a sheet only gets once it is shown. */
    private fun pasteOnFirstFocus(view: View) {
        view.viewTreeObserver.addOnWindowFocusChangeListener(object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) return
                val listener = this
                view.post { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
                if (getView() != null) pasteClipboardLink()
            }
        })
    }

    /** Only a single https link the app would open goes into an empty address field. */
    private fun pasteClipboardLink() {
        if (viewModel.uiState.value.url.isNotEmpty()) return
        val text = requireContext().clipboardText()?.trim() ?: return
        if (text.any(Char::isWhitespace) || !HttpsNavigationPolicy.isNavigable(text)) return
        viewModel.onUrlChanged(text)
    }

    companion object {
        const val TAG = "LinkEditorBottomSheet"

        /** The keyboard resizes the window, so the pinned save button stays above it. */
        private val SPEC = SheetSpec(textInput = true)

        fun newInstance(args: SubjectLinksArgs, linkId: String? = null) =
            LinkEditorBottomSheet().apply { arguments = args.toArguments(linkId) }
    }
}
