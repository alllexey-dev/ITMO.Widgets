package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * Asks before an action: [title], an optional [text] and optional hero [icon] (the calendar access dialog), then
 * [dismissLabel] and [confirmLabel]. [onConfirm] and [onDismiss] (also back and a tap outside) only report the choice;
 * the caller stops showing the dialog. A null [title] leaves the [text] alone, as a message-only `AlertDialog`; an
 * optional [content] below the text holds an option of the action (the force-sign switch of the sport auto-sign).
 */
@Composable
fun ConfirmDialog(
    title: String?,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Painter? = null,
    content: (@Composable () -> Unit)? = null,
) {
    DialogWindow(onDismissRequest = onDismiss) {
        ConfirmDialogSurface(
            title, confirmLabel, dismissLabel, onConfirm, onDismiss, modifier, text, icon, content,
        )
    }
}

/**
 * [ConfirmDialog] without its window: for previews and for hosts that own the window (a Nav3 dialog scene).
 */
@Composable
fun ConfirmDialogSurface(
    title: String?,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Painter? = null,
    content: (@Composable () -> Unit)? = null,
) {
    DialogSurface(
        title = title,
        modifier = modifier,
        icon = icon,
        buttons = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        content = if (text != null || content != null) {
            {
                Column {
                    if (text != null) {
                        Text(
                            text,
                            color = ItmoTheme.colorScheme.onSurfaceVariant,
                            style = ItmoTheme.typography.bodyMedium,
                        )
                    }
                    content?.invoke()
                }
            }
        } else {
            null
        },
    )
}
