package dev.alllexey.itmowidgets.feature.reviews.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ReportDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ReportDialogState
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ReportDialogSurface
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.feature.reviews.Res
import dev.alllexey.itmowidgets.shared.feature.reviews.review_report_comment
import dev.alllexey.itmowidgets.shared.feature.reviews.review_report_offensive
import dev.alllexey.itmowidgets.shared.feature.reviews.review_report_other
import dev.alllexey.itmowidgets.shared.feature.reviews.review_report_send
import dev.alllexey.itmowidgets.shared.feature.reviews.review_report_spam
import dev.alllexey.itmowidgets.shared.feature.reviews.review_report_title
import dev.alllexey.itmowidgets.shared.feature.reviews.review_report_wrong_teacher
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_cancel

/**
 * What [ReportReviewDialog] shows: the picked [reason], the [comment], whether the report is on its way and the
 * [error] of the last attempt, shown under the comment.
 */
@Immutable
data class ReportReviewDialogState(
    val reason: ReviewReportReason? = null,
    val comment: String = "",
    val sending: Boolean = false,
    val error: AppError? = null,
)

/** What the report dialog asks of its host; [onDismiss] gets the cancel button, and back or a tap outside. */
@Immutable
class ReportReviewActions(
    val onReasonSelect: (ReviewReportReason) -> Unit = {},
    val onCommentChange: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * The report dialog the Fragment host calls: keeps the picked reason and the comment across recreation and sends them
 * with [onSend] (`ReportReviewViewModel.send`). [sending] and [error] come from the host; a failure keeps the dialog,
 * which closes only when the host stops showing it after the report was accepted.
 */
@Composable
fun ReportReviewForm(
    sending: Boolean,
    error: AppError?,
    onSend: (ReviewReportReason, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reasonName by rememberSaveable { mutableStateOf<String?>(null) }
    var comment by rememberSaveable { mutableStateOf("") }
    val reason = reasonName?.let(ReviewReportReason::valueOf)
    ReportReviewDialog(
        ReportReviewDialogState(reason, comment, sending, error),
        ReportReviewActions(
            onReasonSelect = { reasonName = it.name },
            onCommentChange = { comment = it },
            onSend = { reason?.let { onSend(it, comment) } },
            onDismiss = onDismiss,
        ),
    )
}

/**
 * `Жалоба на отзыв` (`dialog_report_review.xml`) on the kit's [ReportDialog]: the four [ReviewReportReason]s and an
 * optional comment of up to 500 characters; `Отправить` is enabled once a reason is picked. Back and a tap outside
 * close it only while nothing is being sent.
 */
@Composable
fun ReportReviewDialog(state: ReportReviewDialogState, actions: ReportReviewActions) {
    ReportDialog(
        title = stringResource(Res.string.review_report_title),
        reasons = reasonLabels(),
        state = state.toKit(),
        commentLabel = stringResource(Res.string.review_report_comment),
        sendLabel = stringResource(Res.string.review_report_send),
        dismissLabel = stringResource(CoreRes.string.common_cancel),
        onReasonSelect = { actions.onReasonSelect(ReviewReportReason.entries[it]) },
        onCommentChange = actions.onCommentChange,
        onSend = actions.onSend,
        onDismiss = actions.onDismiss,
    )
}

/** [ReportReviewDialog] without its window, for the previews. */
@Composable
private fun ReportReviewDialogSurface(state: ReportReviewDialogState, actions: ReportReviewActions) {
    ReportDialogSurface(
        title = stringResource(Res.string.review_report_title),
        reasons = reasonLabels(),
        state = state.toKit(),
        commentLabel = stringResource(Res.string.review_report_comment),
        sendLabel = stringResource(Res.string.review_report_send),
        dismissLabel = stringResource(CoreRes.string.common_cancel),
        onReasonSelect = { actions.onReasonSelect(ReviewReportReason.entries[it]) },
        onCommentChange = actions.onCommentChange,
        onSend = actions.onSend,
        onDismiss = actions.onDismiss,
    )
}

/** The labels in [ReviewReportReason]'s order, so a picked index is its ordinal. */
@Composable
private fun reasonLabels(): List<String> = ReviewReportReason.entries.map { stringResource(it.label()) }

@Composable
private fun ReportReviewDialogState.toKit() = ReportDialogState(
    selectedReason = reason?.ordinal,
    comment = comment,
    sending = sending,
    error = error?.let { stringResource(it.textResource()) },
)

private fun ReviewReportReason.label(): StringResource = when (this) {
    ReviewReportReason.OFFENSIVE -> Res.string.review_report_offensive
    ReviewReportReason.WRONG_TEACHER -> Res.string.review_report_wrong_teacher
    ReviewReportReason.SPAM -> Res.string.review_report_spam
    ReviewReportReason.OTHER -> Res.string.review_report_other
}

/*
 * Previews. LX-1c recorded the XML references as `ReportReviewDialog_<state>`, so each state is a function called
 * `ReportReviewDialog` in a holder class of its own: the dialog's surface on the margin its window keeps.
 */

/** Just opened: no reason, `Отправить` disabled. */
internal class ReportReviewDialogEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun ReportReviewDialog() = ReportPreview(ReportReviewDialogState())
}

/** A reason and a comment: ready to send. */
internal class ReportReviewDialogReasonPreview {
    @Preview(name = "reason")
    @Composable
    fun ReportReviewDialog() = ReportPreview(
        ReportReviewDialogState(ReviewReportReason.WRONG_TEACHER, "Ведёт другой предмет"),
    )
}

/** The send failed: its text under the comment, the dialog stays. */
internal class ReportReviewDialogFailedPreview {
    @Preview(name = "failed")
    @Composable
    fun ReportReviewDialog() = ReportPreview(
        ReportReviewDialogState(ReviewReportReason.WRONG_TEACHER, "Ведёт другой предмет", error = AppError.Network),
    )
}

@Composable
private fun ReportPreview(state: ReportReviewDialogState) = ItmoPreview {
    Box(Modifier.padding(ItmoTheme.spacing.section)) { ReportReviewDialogSurface(state, ReportReviewActions()) }
}
