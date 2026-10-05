package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The window of every kit dialog. Back and a tap outside call [onDismissRequest] only while [dismissible]; the
 * caller decides whether to stop showing the dialog.
 */
@Composable
internal fun DialogWindow(
    onDismissRequest: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
        content = content,
    )
}

/**
 * The surface of a material3 alert dialog (`AlertDialog`'s layout and colours, which `MaterialAlertDialogBuilder`
 * shares): 28 dp corners in `surfaceContainerHigh`, 24 dp padding, an optional centred hero [icon], the title in
 * `headlineSmall`, the content and the buttons at the end. With [fullBleedContent] the content spans the whole width
 * (choice rows whose ripple reaches the edges); otherwise it sits on the 24 dp margin like the title.
 */
@Composable
internal fun DialogSurface(
    title: String,
    buttons: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    fullBleedContent: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val padding = ItmoTheme.spacing.section
    Surface(
        modifier
            .sizeIn(minWidth = MinWidth, maxWidth = MaxWidth)
            .semantics { paneTitle = title },
        shape = ItmoTheme.shapes.extraLarge,
        color = ItmoTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(vertical = padding)) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = ItmoTheme.spacing.group)
                        .size(IconSize),
                    tint = ItmoTheme.colorScheme.secondary,
                )
            }
            Text(
                title,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = padding)
                    .semantics { heading() },
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.headlineSmall,
                textAlign = if (icon != null) TextAlign.Center else TextAlign.Start,
            )
            if (content != null) {
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .padding(top = ItmoTheme.spacing.group)
                        .padding(horizontal = if (fullBleedContent) 0.dp else padding),
                ) {
                    content()
                }
            }
            Row(
                Modifier
                    .align(Alignment.End)
                    .padding(top = padding, start = padding, end = padding),
                horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons()
            }
        }
    }
}

/** material3's `DialogMinWidth` and `DialogMaxWidth`. */
private val MinWidth = 280.dp
private val MaxWidth = 560.dp

/** `AlertDialog`'s hero icon. */
private val IconSize = 24.dp
