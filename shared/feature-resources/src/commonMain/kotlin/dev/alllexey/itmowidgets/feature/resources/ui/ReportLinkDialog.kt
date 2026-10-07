package dev.alllexey.itmowidgets.feature.resources.ui

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
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ReportDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ReportDialogState
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ReportDialogSurface
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.feature.resources.Res
import dev.alllexey.itmowidgets.shared.feature.resources.links_report_broken
import dev.alllexey.itmowidgets.shared.feature.resources.links_report_comment
import dev.alllexey.itmowidgets.shared.feature.resources.links_report_other
import dev.alllexey.itmowidgets.shared.feature.resources.links_report_send
import dev.alllexey.itmowidgets.shared.feature.resources.links_report_spam
import dev.alllexey.itmowidgets.shared.feature.resources.links_report_title
import dev.alllexey.itmowidgets.shared.feature.resources.links_report_wrong_subject
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/**
 * What [ReportLinkDialog] shows: the picked [reason], the [comment], whether the report is on its way and the [error]
 * of the last attempt, shown under the comment.
 */
@Immutable
data class ReportLinkDialogState(
    val reason: ResourceReportReason? = null,
    val comment: String = "",
    val sending: Boolean = false,
    val error: AppError? = null,
)

/** What the report dialog asks of its host; [onDismiss] gets the cancel button, and back or a tap outside. */
@Immutable
class ReportLinkActions(
    val onReasonSelect: (ResourceReportReason) -> Unit = {},
    val onCommentChange: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * The report dialog the Fragment host calls: keeps the picked reason and the comment across recreation and sends them
 * with [onSend] (`SubjectLinksViewModel.report`). [sending] and [error] come from the host; a failure keeps the dialog,
 * which closes only when the host stops showing it after the report was accepted.
 */
@Composable
fun ReportLinkForm(
    sending: Boolean,
    error: AppError?,
    onSend: (ResourceReportReason, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reasonName by rememberSaveable { mutableStateOf<String?>(null) }
    var comment by rememberSaveable { mutableStateOf("") }
    val reason = reasonName?.let(ResourceReportReason::valueOf)
    ReportLinkDialog(
        ReportLinkDialogState(reason, comment, sending, error),
        ReportLinkActions(
            onReasonSelect = { reasonName = it.name },
            onCommentChange = { comment = it },
            onSend = { reason?.let { onSend(it, comment) } },
            onDismiss = onDismiss,
        ),
    )
}

/**
 * `Жалоба на ссылку` (`dialog_report_link.xml`) on the kit's [ReportDialog]: the four [ResourceReportReason]s and an
 * optional comment of up to 500 characters; `Отправить` is enabled once a reason is picked and nothing is being sent.
 * Back and a tap outside close it only while nothing is being sent.
 */
@Composable
fun ReportLinkDialog(state: ReportLinkDialogState, actions: ReportLinkActions) {
    ReportDialog(
        title = stringResource(Res.string.links_report_title),
        reasons = reasonLabels(),
        state = state.toKit(),
        commentLabel = stringResource(Res.string.links_report_comment),
        sendLabel = stringResource(Res.string.links_report_send),
        dismissLabel = stringResource(CoreRes.string.common_cancel),
        onReasonSelect = { actions.onReasonSelect(ResourceReportReason.entries[it]) },
        onCommentChange = actions.onCommentChange,
        onSend = actions.onSend,
        onDismiss = actions.onDismiss,
    )
}

/** [ReportLinkDialog] without its window, for the previews. */
@Composable
private fun ReportLinkDialogSurface(state: ReportLinkDialogState, actions: ReportLinkActions) {
    ReportDialogSurface(
        title = stringResource(Res.string.links_report_title),
        reasons = reasonLabels(),
        state = state.toKit(),
        commentLabel = stringResource(Res.string.links_report_comment),
        sendLabel = stringResource(Res.string.links_report_send),
        dismissLabel = stringResource(CoreRes.string.common_cancel),
        onReasonSelect = { actions.onReasonSelect(ResourceReportReason.entries[it]) },
        onCommentChange = actions.onCommentChange,
        onSend = actions.onSend,
        onDismiss = actions.onDismiss,
    )
}

/** The labels in [ResourceReportReason]'s order, so a picked index is its ordinal. */
@Composable
private fun reasonLabels(): List<String> = ResourceReportReason.entries.map { stringResource(it.label()) }

@Composable
private fun ReportLinkDialogState.toKit() = ReportDialogState(
    selectedReason = reason?.ordinal,
    comment = comment,
    sending = sending,
    error = error?.let { stringResource(it.textResource()) },
)

private fun ResourceReportReason.label(): StringResource = when (this) {
    ResourceReportReason.BROKEN -> Res.string.links_report_broken
    ResourceReportReason.WRONG_SUBJECT -> Res.string.links_report_wrong_subject
    ResourceReportReason.SPAM -> Res.string.links_report_spam
    ResourceReportReason.OTHER -> Res.string.links_report_other
}

/*
 * Previews. LX-1c recorded the XML reference as `ReportLinkDialog_reason`, so each state is a function called
 * `ReportLinkDialog` in a holder class of its own: the dialog's surface on the margin its window keeps.
 */

/** Just opened: no reason, `Отправить` disabled. */
internal class ReportLinkDialogEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun ReportLinkDialog() = ReportPreview(ReportLinkDialogState())
}

/** A reason picked: ready to send. */
internal class ReportLinkDialogReasonPreview {
    @Preview(name = "reason")
    @Composable
    fun ReportLinkDialog() = ReportPreview(ReportLinkDialogState(ResourceReportReason.BROKEN))
}

/** The send failed: its text under the comment, the dialog stays. */
internal class ReportLinkDialogFailedPreview {
    @Preview(name = "failed")
    @Composable
    fun ReportLinkDialog() = ReportPreview(
        ReportLinkDialogState(ResourceReportReason.WRONG_SUBJECT, "Ссылка на курс другого семестра", error = AppError.Network),
    )
}

@Composable
private fun ReportPreview(state: ReportLinkDialogState) = ItmoPreview {
    Box(Modifier.padding(ItmoTheme.spacing.section)) { ReportLinkDialogSurface(state, ReportLinkActions()) }
}
