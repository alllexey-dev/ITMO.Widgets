package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import org.jetbrains.compose.resources.painterResource

/**
 * The one choice pattern for two to four short [options] and in-screen tabs (`Моё` / `Запись`, `Баллы` / `Пары` /
 * `Материалы`): Material 3 Expressive's connected button group of toggle buttons at equal widths, a check on the
 * selected one while up to three options leave room for it. A selection is always made; [onSelect] gets the index of
 * a newly chosen option. [enabled] false shows a choice that cannot change now; [testTags] tag the options in order.
 * Filters are chips, longer choices a list or a sheet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTags: List<String> = emptyList(),
) {
    require(options.size in 2..MAX_OPTIONS) { "A segmented choice holds 2..$MAX_OPTIONS options, got ${options.size}" }
    require(selectedIndex in options.indices) { "selectedIndex $selectedIndex is outside ${options.indices}" }
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, label ->
            val checked = index == selectedIndex
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(index) },
                // A single choice: TalkBack reads a radio button and its selection.
                modifier = Modifier.weight(1f).then(testTags.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier).semantics {
                    role = Role.RadioButton
                    selected = checked
                },
                enabled = enabled,
                shapes = connectedShapes(index, options.lastIndex),
                contentPadding = PaddingValues(horizontal = LabelPadding),
            ) {
                if (checked && options.size <= CHECK_MAX_OPTIONS) {
                    Icon(
                        painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        Modifier.padding(end = CheckGap).size(CheckSize),
                    )
                }
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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

/** More options than this need chips, a list or a sheet. */
private const val MAX_OPTIONS = 4

/** Four labels do not fit with the check. */
private const val CHECK_MAX_OPTIONS = 3

private val LabelPadding = 8.dp
private val CheckSize = 18.dp
private val CheckGap = 6.dp
