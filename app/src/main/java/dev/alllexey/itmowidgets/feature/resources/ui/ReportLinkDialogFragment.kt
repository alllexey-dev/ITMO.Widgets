package dev.alllexey.itmowidgets.feature.resources.ui

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
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * A reason and an optional comment, drawn by `ReportLinkForm` of `:shared:feature-resources` on the kit's report
 * dialog, which brings its own window: back and a tap outside close it while nothing is being sent. The Fragment's own
 * dialog only anchors that window, so it is transparent, undimmed and lets touches through. The dialog stays until
 * the report is accepted; a failure shows its text under the comment.
 */
@AndroidEntryPoint
class ReportLinkDialogFragment : DialogFragment() {
    private val viewModel: SubjectLinksViewModel by viewModel()
    private val linkId: String by lazy { checkNotNull(requireArguments().getString(SubjectLinksArgs.LINK_ID)) }

    /** The last failure, cleared by the next send. */
    private var failure by mutableStateOf<AppError?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        LinkEvent.Done, LinkEvent.Saved -> dismiss()
                        is LinkEvent.Failed -> failure = event.error
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
            ReportLinkForm(
                sending = state.busy,
                error = failure,
                onSend = { reason, comment ->
                    failure = null
                    viewModel.report(linkId, reason, comment.trim().ifEmpty { null })
                },
                onDismiss = ::dismiss,
            )
        }

    companion object {
        const val TAG = "ReportLinkDialogFragment"

        fun newInstance(args: SubjectLinksArgs, linkId: String) =
            ReportLinkDialogFragment().apply { arguments = args.toArguments(linkId) }
    }
}
