package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * Tells the user something and has one button: an optional [title], the [text] and [buttonLabel]. The button, back
 * and a tap outside all call [onDismiss]; the caller stops showing the dialog.
 *
 * Under the iOS style it is an iOS alert ([IosAlertSurface]) whose one capsule is the preferred action.
 */
@Composable
fun InfoDialog(
    title: String?,
    text: String,
    buttonLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DialogWindow(onDismissRequest = onDismiss) {
        InfoDialogSurface(title, text, buttonLabel, onDismiss, modifier)
    }
}

/**
 * [InfoDialog] without its window: for previews and for hosts that own the window (a Nav3 dialog scene).
 */
@Composable
fun InfoDialogSurface(
    title: String?,
    text: String,
    buttonLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> DialogSurface(
            title = title,
            modifier = modifier,
            buttons = { TextButton(onClick = onDismiss) { Text(buttonLabel) } },
            content = {
                Text(text, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            },
        )
        ItmoPlatformStyle.Ios -> IosAlertSurface(
            title = title,
            buttons = listOf(IosAlertButton(buttonLabel, onDismiss, IosAlertButtonKind.Preferred)),
            modifier = modifier,
            message = text,
        )
    }
}
