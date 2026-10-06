package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * A single choice of two to four short [options] with a selection always made, side by side at equal widths: the
 * friend picker's scope, the sport tabs, a single-select chip set. With [ItmoTheme.expressive] it is the connected
 * button group of toggle buttons; otherwise today's segmented buttons. [onSelect] gets the index of a newly chosen
 * option; tapping the selected one does nothing.
 */
@Composable
fun ItmoButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.size in 2..MAX_OPTIONS) { "A button group holds 2..$MAX_OPTIONS options, got ${options.size}" }
    require(selectedIndex in options.indices) { "selectedIndex $selectedIndex is outside ${options.indices}" }
    val choose = { index: Int -> if (index != selectedIndex) onSelect(index) }
    if (ItmoTheme.expressive) {
        ConnectedGroup(options, selectedIndex, choose, modifier)
    } else {
        SegmentedGroup(options, selectedIndex, choose, modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedGroup(options: List<String>, selectedIndex: Int, choose: (Int) -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, label ->
            ToggleButton(
                checked = index == selectedIndex,
                onCheckedChange = { choose(index) },
                modifier = Modifier.weight(1f),
                shapes = connectedShapes(index, options.lastIndex),
            ) { OptionLabel(label) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun connectedShapes(index: Int, lastIndex: Int): ToggleButtonShapes = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

@Composable
private fun SegmentedGroup(options: List<String>, selectedIndex: Int, choose: (Int) -> Unit, modifier: Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { choose(index) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.weight(1f),
                label = { OptionLabel(label) },
            )
        }
    }
}

@Composable
private fun OptionLabel(label: String) {
    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** More options than this need a menu or chips, not a group of equal buttons. */
private const val MAX_OPTIONS = 4
