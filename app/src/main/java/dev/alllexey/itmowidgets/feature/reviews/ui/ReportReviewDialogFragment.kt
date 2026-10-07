package dev.alllexey.itmowidgets.feature.reviews.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewEvent
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * A reason and an optional comment, drawn by `ReportReviewForm` of `:shared:feature-reviews` on the kit's report
 * dialog, which brings its own window: back and a tap outside close it while nothing is being sent. The Fragment's own
 * dialog only anchors that window, so it is transparent, undimmed and lets touches through. The dialog stays until
 * the report is accepted; a failure shows its text under the comment.
 */
@AndroidEntryPoint
class ReportReviewDialogFragment : DialogFragment() {
    private val viewModel: ReportReviewViewModel by viewModel()

    /** The last failure, cleared by the next send. */
    private var failure by mutableStateOf<AppError?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        ReportReviewEvent.Done -> dismiss()
                        is ReportReviewEvent.Failed -> failure = event.error
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = ComponentDialog(requireContext()).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.run {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            val state by viewModel.uiState.collectAsState()
            ReportReviewForm(
                sending = state.sending,
                error = failure,
                onSend = { reason, comment ->
                    failure = null
                    viewModel.send(reason, comment)
                },
                onDismiss = ::dismiss,
            )
        }

    companion object {
        const val TAG = "ReportReviewDialogFragment"

        fun newInstance(args: TeacherReviewArgs, reviewId: String) = ReportReviewDialogFragment().apply {
            arguments = bundleOf(
                TeacherReviewArgs.TEACHER_ISU to args.teacherIsu,
                TeacherReviewArgs.TEACHER_NAME to args.teacherName,
                TeacherReviewArgs.REVIEW_ID to reviewId,
            )
        }
    }
}
