package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import dev.alllexey.itmowidgets.designsystem.platform.ItmoHapticEvent
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.platform.rememberItmoHaptics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics

/**
 * Asks before an action: [title], an optional [text] and optional hero [icon] (the calendar access dialog), then
 * [dismissLabel] and [confirmLabel]. [onConfirm] and [onDismiss] (also back and a tap outside) only report the choice;
 * the caller stops showing the dialog. A null [title] leaves the [text] alone, as a message-only `AlertDialog`; an
 * optional [content] below the text holds an option of the action (the force-sign switch of the sport auto-sign).
 *
 * Under the iOS style it is an iOS alert ([IosAlertSurface]): the cancel capsule, then the confirm capsule as the
 * preferred action, or in system red with a warning haptic when [destructive]; the [content] sits on the text's inset.
 * The hero [icon] is not drawn there; Material ignores [destructive].
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
    destructive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    DialogWindow(onDismissRequest = onDismiss) {
        ConfirmDialogSurface(
            title, confirmLabel, dismissLabel, onConfirm, onDismiss, modifier, text, icon, destructive, content,
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
    destructive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> DialogSurface(
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
        ItmoPlatformStyle.Ios -> {
            val haptics = rememberItmoHaptics()
            val confirm = if (destructive) {
                IosAlertButton(
                    confirmLabel,
                    onClick = {
                        haptics.perform(ItmoHapticEvent.Warning)
                        onConfirm()
                    },
                    kind = IosAlertButtonKind.Destructive,
                )
            } else {
                IosAlertButton(confirmLabel, onConfirm, IosAlertButtonKind.Preferred)
            }
            IosAlertSurface(
                title = title,
                buttons = listOf(IosAlertButton(dismissLabel, onDismiss), confirm),
                modifier = modifier,
                message = text,
                // On the text's inset, as the Material dialog keeps the content on its margin.
                content = content?.let {
                    { Box(Modifier.padding(horizontal = IosMetrics.alertTextInset)) { it() } }
                },
            )
        }
    }
}
