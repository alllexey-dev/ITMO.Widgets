package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

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

/** `dialog_report_*.xml`'s `maxLines`. */
private const val COMMENT_MAX_LINES = 4
