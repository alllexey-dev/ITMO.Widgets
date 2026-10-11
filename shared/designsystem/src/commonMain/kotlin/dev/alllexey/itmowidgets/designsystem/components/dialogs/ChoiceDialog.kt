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
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * Picks one of [options]. With a [selectedIndex] the rows are radio buttons with the current choice marked
 * (`setSingleChoiceItems`); with null they are plain items (`setItems`). A tap reports [onSelect]; the caller applies
 * it and stops showing the dialog, as the View dialogs closed on a pick. [dismissLabel] adds a cancel button;
 * [onDismiss] also gets back and a tap outside. [descriptions] puts a line under an option, by index (Material only).
 *
 * Under the iOS style it is an iOS alert: the single choice as a list with a trailing checkmark in the tint on the
 * current row, the plain items as stacked action capsules, the cancel capsule last.
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
    descriptions: List<String?> = emptyList(),
) {
    DialogWindow(onDismissRequest = onDismiss) {
        ChoiceDialogSurface(title, options, selectedIndex, onSelect, onDismiss, modifier, dismissLabel, descriptions)
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
    descriptions: List<String?> = emptyList(),
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material ->
            MaterialChoiceDialog(title, options, selectedIndex, onSelect, onDismiss, modifier, dismissLabel, descriptions)
        ItmoPlatformStyle.Ios ->
            IosChoiceDialog(title, options, selectedIndex, onSelect, onDismiss, modifier, dismissLabel)
    }
}

@Composable
private fun MaterialChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
    dismissLabel: String?,
    descriptions: List<String?>,
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
                    val description = descriptions.getOrNull(index)
                    if (selectedIndex == null) {
                        ChoiceRow(option, Modifier.clickable(role = Role.Button) { onSelect(index) }, description = description)
                    } else {
                        ChoiceRow(
                            option,
                            Modifier.selectable(index == selectedIndex, role = Role.RadioButton) { onSelect(index) },
                            description = description,
                        ) {
                            RadioButton(selected = index == selectedIndex, onClick = null)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun IosChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
    dismissLabel: String?,
) {
    val cancel = listOfNotNull(dismissLabel?.let { IosAlertButton(it, onDismiss) })
    if (selectedIndex == null) {
        val items = options.mapIndexed { index, option -> IosAlertButton(option, onClick = { onSelect(index) }) }
        IosAlertSurface(title = title, buttons = items + cancel, modifier = modifier)
        return
    }
    IosAlertSurface(
        title = title,
        buttons = cancel,
        modifier = modifier,
        content = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                options.forEachIndexed { index, option ->
                    IosAlertCheckRow(
                        option,
                        checked = index == selectedIndex,
                        first = index == 0,
                        interaction = Modifier.selectable(index == selectedIndex, role = Role.RadioButton) {
                            onSelect(index)
                        },
                    )
                }
            }
        },
    )
}

/**
 * A full-width row of a choice list: at least a touch target high, text on the dialog's 24 dp margin, and an optional
 * [description] line under it.
 */
@Composable
internal fun ChoiceRow(
    text: String,
    modifier: Modifier,
    description: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(horizontal = ItmoTheme.spacing.section, vertical = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(if (leading != null) Modifier.padding(start = ItmoTheme.spacing.group) else Modifier) {
            Text(text, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            }
        }
    }
}
