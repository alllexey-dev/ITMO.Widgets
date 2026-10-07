package dev.alllexey.itmowidgets.feature.reviews.ui

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.designsystem.host.SheetDismissal
import dev.alllexey.itmowidgets.designsystem.host.SheetSpec
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorEvent
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Writes or edits the viewer's review, drawn by `ReviewEditorSheet` of `:shared:feature-reviews`. A form sheet: no
 * handle, no drag, no tap outside, the window resized for the keyboard; back and the close button ask before
 * discarding changes, so they are never lost silently. The Fragment keeps the stable entry points (class name, [TAG],
 * [newInstance]) and performs the effects: closing after a save, the snackbar of a failed one.
 */
@AndroidEntryPoint
class ReviewEditorBottomSheet : ItmoBottomSheetFragment() {
    private val viewModel: ReviewEditorViewModel by viewModel()

    /** `Не сохранять отзыв?` is up; like the View dialog, the question is not restored after recreation. */
    private var discarding by mutableStateOf(false)

    override val spec: SheetSpec get() = SPEC

    @Composable
    override fun SheetContent() {
        val form by viewModel.uiState.collectAsState()
        ReviewEditorSheet(
            form = form,
            teacherName = viewModel.teacherName,
            discarding = discarding,
            actions = ReviewEditorActions(
                onSubjectChange = viewModel::onSubjectChanged,
                onTextChange = viewModel::onTextChanged,
                onAnonymousChange = viewModel::onAnonymousChanged,
                onSave = viewModel::save,
                onClose = ::onCloseRequest,
                onDiscard = ::dismiss,
                onKeepEditing = { discarding = false },
            ),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { event ->
            when (event) {
                ReviewEditorEvent.Saved -> dismiss()
                is ReviewEditorEvent.Failed ->
                    Snackbar.make(view, event.error.messageRes(), Snackbar.LENGTH_LONG).show()
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /** Back and the close button: leaving with unsaved changes asks first; an untouched form simply closes. */
    override fun onCloseRequest() {
        if (viewModel.hasChanges()) discarding = true else dismiss()
    }

    companion object {
        const val TAG = "ReviewEditorBottomSheet"

        /** Content height, no drag or tap outside, resized above the keyboard, as the View sheet opened. */
        private val SPEC = SheetSpec(dismissal = SheetDismissal.Form, textInput = true)

        fun newInstance(args: TeacherReviewArgs) = ReviewEditorBottomSheet().apply {
            arguments = bundleOf(TeacherReviewArgs.TEACHER_ISU to args.teacherIsu, TeacherReviewArgs.TEACHER_NAME to args.teacherName)
        }
    }
}
