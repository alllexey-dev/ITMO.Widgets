package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * Picks one of [options]. With a [selectedIndex] the rows are radio buttons with the current choice marked
 * (`setSingleChoiceItems`); with null they are plain items (`setItems`). A tap reports [onSelect]; the caller applies
 * it and stops showing the dialog, as the View dialogs closed on a pick. [dismissLabel] adds a cancel button;
 * [onDismiss] also gets back and a tap outside.
 */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
) {
    DialogWindow(onDismissRequest = onDismiss) {
        ChoiceDialogSurface(title, options, selectedIndex, onSelect, onDismiss, modifier, dismissLabel)
    }
}

/** [ChoiceDialog] without its window: for previews and for hosts that own the window (a Nav3 dialog scene). */
@Composable
fun ChoiceDialogSurface(
    title: String,
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
) {
    DialogSurface(
        title = title,
        modifier = modifier,
        fullBleedContent = true,
        buttons = {
            if (dismissLabel != null) TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
        content = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                options.forEachIndexed { index, option ->
                    if (selectedIndex == null) {
                        ChoiceRow(option, Modifier.clickable(role = Role.Button) { onSelect(index) })
                    } else {
                        ChoiceRow(
                            option,
                            Modifier.selectable(index == selectedIndex, role = Role.RadioButton) { onSelect(index) },
                        ) {
                            RadioButton(selected = index == selectedIndex, onClick = null)
                        }
                    }
                }
            }
        },
    )
}

/** A full-width row of a choice list: at least a touch target high, text on the dialog's 24 dp margin. */
@Composable
internal fun ChoiceRow(
    text: String,
    modifier: Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(horizontal = ItmoTheme.spacing.section, vertical = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Text(
                text,
                Modifier.padding(start = ItmoTheme.spacing.group),
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyLarge,
            )
        } else {
            Text(text, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
        }
    }
}
