package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.common_done
import dev.alllexey.itmowidgets.shared.feature.sport.sport_filter_search_hint
import dev.alllexey.itmowidgets.shared.feature.sport.sport_filter_select_type
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_search

/** One row of the section picker: the catalogue's [name] and its shortened [label]. */
@Immutable
data class SportSectionOption(val name: SectionName, val label: String)

/**
 * The picker's rows in today's order (`SportSignFragment.showMultiSelectSearchableDialog`): the selected sections
 * first, then those in [used] (signed or queued), then by label. Labels go through [SectionName.shorten], and the
 * used check goes back through [SectionName.deshorten], as the View dialog compared its labels.
 */
fun sportSectionOptions(
    available: List<SectionName>,
    selected: Set<SectionName>,
    used: Set<SectionName>,
): List<SportSectionOption> = available
    .map { SportSectionOption(it, it.shorten()) }
    .sortedWith(
        compareBy<SportSectionOption> { it.name !in selected }
            .thenBy { SectionName.deshorten(it.label) !in used }
            .thenBy { it.label },
    )

/**
 * The rows whose label contains [query], case-insensitively and independent of the device locale (`lowercase()` is
 * the invariant one, CL-09); a blank query keeps every row.
 */
fun List<SportSectionOption>.matching(query: String): List<SportSectionOption> {
    val needle = query.lowercase()
    if (needle.isEmpty()) return this
    return filter { it.label.lowercase().contains(needle) }
}

/**
 * The confirmed selection: the checked rows' labels mapped back through [SectionName.deshorten], as the View dialog
 * returned them. Two sections with one short label resolve to the same full name.
 */
fun List<SportSectionOption>.confirmedSelection(checked: Set<SectionName>): Set<SectionName> =
    filter { it.name in checked }.mapTo(LinkedHashSet()) { SectionName.deshorten(it.label) }

object SportSectionPickerTestTags {
    const val SEARCH = "sport_section_picker_search"
    const val LIST = "sport_section_picker_list"
    const val CONFIRM = "sport_section_picker_confirm"
    const val CANCEL = "sport_section_picker_cancel"
}

/**
 * The section multi-select with search in a dialog window: [onConfirm] gets the selection once on `Готово`;
 * [onDismiss] gets `Отмена`, back and a tap outside. The search text and the checks survive recreation.
 *
 * The window spans the screen and the surface keeps `MaterialAlertDialogBuilder`'s insets (24 dp at the sides,
 * 80 dp above and below), so the dialog is as wide as the View one on a phone. The platform default width is off:
 * besides the insets, a text field in a default-width window keeps Robolectric's layout from settling.
 */
@Composable
fun SportSectionPickerDialog(
    available: List<SectionName>,
    selected: Set<SectionName>,
    used: Set<SectionName>,
    onConfirm: (Set<SectionName>) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SportSectionPickerSurface(
            available,
            selected,
            used,
            onConfirm,
            onDismiss,
            Modifier.padding(horizontal = ItmoTheme.spacing.section, vertical = DialogVerticalInset),
        )
    }
}

/** [SportSectionPickerDialog] without its window: for previews and for hosts that own the window. */
@Composable
fun SportSectionPickerSurface(
    available: List<SectionName>,
    selected: Set<SectionName>,
    used: Set<SectionName>,
    onConfirm: (Set<SectionName>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember(available, selected, used) { sportSectionOptions(available, selected, used) }
    var query by rememberSaveable { mutableStateOf("") }
    var checkedRaw by rememberSaveable { mutableStateOf(selected.map { it.raw }) }
    val checked = checkedRaw.mapTo(HashSet(), ::SectionName)
    val title = stringResource(Res.string.sport_filter_select_type)

    Surface(
        modifier
            .sizeIn(minWidth = DialogMinWidth, maxWidth = DialogMaxWidth)
            .fillMaxWidth()
            .semantics { paneTitle = title },
        shape = ItmoTheme.shapes.extraLarge,
        color = ItmoTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(top = ItmoTheme.spacing.group, bottom = ItmoTheme.spacing.section)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ItmoTheme.spacing.section)
                    .testTag(SportSectionPickerTestTags.SEARCH),
                label = { Text(stringResource(Res.string.sport_filter_search_hint)) },
                leadingIcon = { Icon(painterResource(KitRes.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            LazyColumn(
                Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = ItmoTheme.spacing.compact)
                    .padding(top = ItmoTheme.spacing.compact)
                    .testTag(SportSectionPickerTestTags.LIST),
            ) {
                items(options.matching(query), key = { it.name.raw }) { option ->
                    SectionRow(option.label, option.name in checked) { isChecked ->
                        checkedRaw = if (isChecked) checkedRaw + option.name.raw else checkedRaw - option.name.raw
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ItmoTheme.spacing.section)
                    .padding(top = ItmoTheme.spacing.compact),
                horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact, Alignment.End),
            ) {
                TextButton(onClick = onDismiss, Modifier.testTag(SportSectionPickerTestTags.CANCEL)) {
                    Text(stringResource(CoreRes.string.common_cancel))
                }
                TextButton(
                    onClick = { onConfirm(options.confirmedSelection(checked)) },
                    Modifier.testTag(SportSectionPickerTestTags.CONFIRM),
                ) {
                    Text(stringResource(Res.string.common_done))
                }
            }
        }
    }
}

/** `item_multi_selectable`: the whole row is the checkbox's target, at least a touch target high. */
@Composable
private fun SectionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = ItmoTheme.spacing.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            label,
            Modifier.padding(start = ItmoTheme.spacing.compact),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodyLarge,
        )
    }
}

/** Material's alert dialog width range, as `MaterialAlertDialogBuilder` sized the View dialog. */
private val DialogMinWidth = 280.dp
private val DialogMaxWidth = 560.dp

/** `MaterialAlertDialogBuilder`'s top and bottom background inset. */
private val DialogVerticalInset = 80.dp
