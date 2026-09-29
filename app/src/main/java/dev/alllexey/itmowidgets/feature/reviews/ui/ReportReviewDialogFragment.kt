package dev.alllexey.itmowidgets.feature.reviews.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.databinding.DialogReportReviewBinding
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewEvent
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import kotlinx.coroutines.launch

/** A reason and an optional comment; the dialog stays until the report is accepted. */
@AndroidEntryPoint
class ReportReviewDialogFragment : DialogFragment() {
    private val viewModel: ReportReviewViewModel by viewModels()
    private lateinit var form: DialogReportReviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.sending.collect { updateSendButton() } }
                viewModel.events.collect { event ->
                    when (event) {
                        ReportReviewEvent.Done -> dismiss()
                        is ReportReviewEvent.Failed -> form.commentLayout.error = event.text.resolve(requireContext())
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        form = DialogReportReviewBinding.inflate(layoutInflater)
        form.reasons.setOnCheckedChangeListener { _, _ -> updateSendButton() }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.review_report_title)
            .setView(form.root)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.review_report_send, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { send() }
                    updateSendButton()
                }
            }
    }

    private fun selectedReason(): ReviewReportReason? = when (form.reasons.checkedRadioButtonId) {
        R.id.reason_offensive -> ReviewReportReason.OFFENSIVE
        R.id.reason_wrong_teacher -> ReviewReportReason.WRONG_TEACHER
        R.id.reason_spam -> ReviewReportReason.SPAM
        R.id.reason_other -> ReviewReportReason.OTHER
        else -> null
    }

    private fun send() {
        val reason = selectedReason() ?: return
        form.commentLayout.error = null
        viewModel.send(reason, form.comment.text?.toString())
    }

    private fun updateSendButton() {
        if (!::form.isInitialized) return
        (dialog as? AlertDialog)?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled =
            !viewModel.sending.value && selectedReason() != null
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
