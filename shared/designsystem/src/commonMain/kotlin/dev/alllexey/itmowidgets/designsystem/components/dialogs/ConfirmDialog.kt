package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * Asks before an action: [title], an optional [text] and optional hero [icon] (the calendar access dialog), then
 * [dismissLabel] and [confirmLabel]. [onConfirm] and [onDismiss] (also back and a tap outside) only report the choice;
 * the caller stops showing the dialog.
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Painter? = null,
) {
    DialogWindow(onDismissRequest = onDismiss) {
        ConfirmDialogSurface(title, confirmLabel, dismissLabel, onConfirm, onDismiss, modifier, text, icon)
    }
}

/**
 * [ConfirmDialog] without its window: for previews and for hosts that own the window (a Nav3 dialog scene).
 */
@Composable
fun ConfirmDialogSurface(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Painter? = null,
) {
    DialogSurface(
        title = title,
        modifier = modifier,
        icon = icon,
        buttons = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        content = text?.let {
            {
                Text(text, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            }
        },
    )
}
