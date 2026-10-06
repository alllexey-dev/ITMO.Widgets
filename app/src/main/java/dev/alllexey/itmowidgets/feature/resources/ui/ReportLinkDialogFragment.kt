package dev.alllexey.itmowidgets.feature.resources.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.databinding.DialogReportLinkBinding
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** A reason and an optional comment; the dialog stays until the report is accepted. */
@AndroidEntryPoint
class ReportLinkDialogFragment : DialogFragment() {
    private val viewModel: SubjectLinksViewModel by viewModel()
    private val linkId: String by lazy { checkNotNull(requireArguments().getString(SubjectLinksArgs.LINK_ID)) }
    private lateinit var form: DialogReportLinkBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { updateSendButton() } }
                viewModel.events.collect { event ->
                    when (event) {
                        LinkEvent.Done, LinkEvent.Saved -> dismiss()
                        is LinkEvent.Failed -> form.commentLayout.error = getString(event.error.messageRes())
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        form = DialogReportLinkBinding.inflate(layoutInflater)
        form.reasons.setOnCheckedChangeListener { _, _ -> updateSendButton() }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.links_report_title)
            .setView(form.root)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.links_report_send, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { send() }
                    updateSendButton()
                }
            }
    }

    private fun selectedReason(): ResourceReportReason? = when (form.reasons.checkedRadioButtonId) {
        R.id.reason_broken -> ResourceReportReason.BROKEN
        R.id.reason_wrong_subject -> ResourceReportReason.WRONG_SUBJECT
        R.id.reason_spam -> ResourceReportReason.SPAM
        R.id.reason_other -> ResourceReportReason.OTHER
        else -> null
    }

    private fun send() {
        val reason = selectedReason() ?: return
        if (viewModel.uiState.value.busy) return
        form.commentLayout.error = null
        viewModel.report(linkId, reason, form.comment.text?.toString()?.trim()?.ifEmpty { null })
    }

    private fun updateSendButton() {
        if (!::form.isInitialized) return
        (dialog as? AlertDialog)?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled =
            !viewModel.uiState.value.busy && selectedReason() != null
    }

    companion object {
        const val TAG = "ReportLinkDialogFragment"

        fun newInstance(args: SubjectLinksArgs, linkId: String) =
            ReportLinkDialogFragment().apply { arguments = args.toArguments(linkId) }
    }
}
