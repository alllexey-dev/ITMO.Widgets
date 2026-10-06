package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics

/**
 * What a [ReportDialog] shows: the picked reason (an index into its reasons), the comment, whether the report is being
 * sent and the error of the last attempt.
 */
@Immutable
data class ReportDialogState(
    val selectedReason: Int? = null,
    val comment: String = "",
    val sending: Boolean = false,
    val error: String? = null,
) {
    /** Sending needs a reason and is not repeated while a report is on its way. */
    val canSend: Boolean get() = selectedReason != null && !sending

    /**
     * Back and a tap outside close the dialog only while nothing is being sent, so a slow report cannot vanish with
     * its error; the cancel button always closes it.
     */
    val dismissibleByGesture: Boolean get() = !sending

    companion object {
        /** `dialog_report_*.xml`'s comment limit. */
        const val COMMENT_MAX_LENGTH = 500

        /** [text] cut to [COMMENT_MAX_LENGTH], as the View field's `maxLength` filter did. */
        fun limitComment(text: String): String = text.take(COMMENT_MAX_LENGTH)
    }
}

/**
 * Reports a review or a link (03 A8: the two dialogs differ only in strings): one of [reasons] and an optional comment
 * labelled [commentLabel] with a counter and an error slot ([ReportDialogState.error]). [onSend] never closes the
 * dialog: it stays while sending and after a failure, until the caller stops showing it once the report is accepted.
 * [onDismiss] gets the cancel button and, while nothing is being sent, back and a tap outside.
 *
 * Under the iOS style it is an iOS alert: the reasons as a checkmark list, the comment in an alert's text field capsule
 * with the error and the counter under it, then the cancel capsule and the send capsule as the preferred action, which
 * shows a spinner while sending.
 */
@Composable
fun ReportDialog(
    title: String,
    reasons: List<String>,
    state: ReportDialogState,
    commentLabel: String,
    sendLabel: String,
    dismissLabel: String,
    onReasonSelect: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DialogWindow(onDismissRequest = onDismiss, dismissible = state.dismissibleByGesture) {
        ReportDialogSurface(
            title, reasons, state, commentLabel, sendLabel, dismissLabel,
            onReasonSelect, onCommentChange, onSend, onDismiss, modifier,
        )
    }
}

/** [ReportDialog] without its window: for previews and for hosts that own the window (a Nav3 dialog scene). */
@Composable
fun ReportDialogSurface(
    title: String,
    reasons: List<String>,
    state: ReportDialogState,
    commentLabel: String,
    sendLabel: String,
    dismissLabel: String,
    onReasonSelect: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        IosReportDialog(
            title, reasons, state, commentLabel, sendLabel, dismissLabel,
            onReasonSelect, onCommentChange, onSend, onDismiss, modifier,
        )
        return
    }
    DialogSurface(
        title = title,
        modifier = modifier,
        fullBleedContent = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
            ProgressButton(
                sendLabel,
                onClick = onSend,
                style = ProgressButtonStyle.Text,
                inProgress = state.sending,
                enabled = state.selectedReason != null,
            )
        },
        content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.selectableGroup()) {
                    reasons.forEachIndexed { index, reason ->
                        val selected = index == state.selectedReason
                        ChoiceRow(
                            reason,
                            Modifier.selectable(selected, enabled = !state.sending, role = Role.RadioButton) {
                                onReasonSelect(index)
                            },
                        ) {
                            RadioButton(selected = selected, onClick = null, enabled = !state.sending)
                        }
                    }
                }
                CommentField(state, commentLabel, onCommentChange)
            }
        },
    )
}

@Composable
private fun CommentField(state: ReportDialogState, label: String, onCommentChange: (String) -> Unit) {
    OutlinedTextField(
        value = state.comment,
        onValueChange = { onCommentChange(ReportDialogState.limitComment(it)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.section)
            .padding(top = ItmoTheme.spacing.compact),
        enabled = !state.sending,
        label = { Text(label) },
        isError = state.error != null,
        supportingText = {
            Row {
                Text(state.error.orEmpty(), Modifier.weight(1f))
                Text("${state.comment.length}/${ReportDialogState.COMMENT_MAX_LENGTH}")
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        maxLines = COMMENT_MAX_LINES,
    )
}

@Composable
private fun IosReportDialog(
    title: String,
    reasons: List<String>,
    state: ReportDialogState,
    commentLabel: String,
    sendLabel: String,
    dismissLabel: String,
    onReasonSelect: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    IosAlertSurface(
        title = title,
        modifier = modifier,
        buttons = listOf(
            IosAlertButton(dismissLabel, onDismiss),
            IosAlertButton(
                sendLabel,
                onSend,
                IosAlertButtonKind.Preferred,
                enabled = state.selectedReason != null,
                inProgress = state.sending,
            ),
        ),
        content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.selectableGroup()) {
                    reasons.forEachIndexed { index, reason ->
                        val selected = index == state.selectedReason
                        IosAlertCheckRow(
                            reason,
                            checked = selected,
                            first = index == 0,
                            interaction = Modifier.selectable(
                                selected,
                                enabled = !state.sending,
                                role = Role.RadioButton,
                            ) { onReasonSelect(index) },
                            enabled = !state.sending,
                        )
                    }
                }
                IosCommentField(state, commentLabel, onCommentChange)
            }
        },
    )
}

/** `UIAlertController`'s text field: a capsule-cornered field on the fill, [label] as its placeholder. */
@Composable
private fun IosCommentField(state: ReportDialogState, label: String, onCommentChange: (String) -> Unit) {
    val colors = ItmoTheme.iosColors
    val enabled = !state.sending
    val textStyle = ItmoTheme.typography.bodyLarge.copy(color = if (enabled) colors.label else colors.tertiaryLabel)
    Column(Modifier.padding(horizontal = IosMetrics.alertFieldInset).padding(top = IosMetrics.alertContentGap)) {
        BasicTextField(
            value = state.comment,
            onValueChange = { onCommentChange(ReportDialogState.limitComment(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            enabled = enabled,
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            maxLines = COMMENT_MAX_LINES,
            cursorBrush = SolidColor(ItmoTheme.colorScheme.primary),
            decorationBox = { field ->
                Box(
                    Modifier
                        .heightIn(min = IosMetrics.alertButtonHeight)
                        .background(colors.tertiarySystemFill, RoundedCornerShape(IosMetrics.alertButtonHeight / 2))
                        .padding(horizontal = IosMetrics.alertFieldPadding, vertical = IosFieldVerticalPadding),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (state.comment.isEmpty()) Text(label, color = colors.tertiaryLabel, style = textStyle)
                    field()
                }
            },
        )
        Row(Modifier.padding(horizontal = IosMetrics.alertFieldPadding).padding(top = IosFieldNoteGap)) {
            // An empty text would be a node without a label; the counter keeps the line's height.
            Box(Modifier.weight(1f)) {
                state.error?.let { Text(it, color = colors.systemRed, style = ItmoTheme.typography.bodySmall) }
            }
            Text(
                "${state.comment.length}/${ReportDialogState.COMMENT_MAX_LENGTH}",
                color = colors.secondaryLabel,
                style = ItmoTheme.typography.bodySmall,
            )
        }
    }
}

/** One 22 pt body line in the 48 pt field capsule. */
private val IosFieldVerticalPadding = 13.dp

/** Between the field and its error and counter line. */
private val IosFieldNoteGap = 6.dp

/** `dialog_report_*.xml`'s `maxLines`. */
private const val COMMENT_MAX_LINES = 4
