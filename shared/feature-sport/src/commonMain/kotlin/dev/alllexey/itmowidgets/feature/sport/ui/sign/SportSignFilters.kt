package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import dev.alllexey.itmowidgets.designsystem.gesture.tabSwipeHandover
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.common_reset
import dev.alllexey.itmowidgets.shared.feature.sport.sport_any_building
import dev.alllexey.itmowidgets.shared.feature.sport.sport_any_teacher
import dev.alllexey.itmowidgets.shared.feature.sport.sport_any_time
import dev.alllexey.itmowidgets.shared.feature.sport.sport_filter_auto_sign
import dev.alllexey.itmowidgets.shared.feature.sport.sport_filter_available
import dev.alllexey.itmowidgets.shared.feature.sport.sport_filter_building
import dev.alllexey.itmowidgets.shared.feature.sport.sport_filter_friends
import dev.alllexey.itmowidgets.shared.feature.sport.sport_filter_select_type
import dev.alllexey.itmowidgets.shared.feature.sport.sport_filter_teacher
import dev.alllexey.itmowidgets.shared.feature.sport.sport_filter_time
import dev.alllexey.itmowidgets.shared.feature.sport.sport_filter_type
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_search

/** The filters header's callbacks, one per `SportSignViewModel` method it drives; a null value means "any". */
@Immutable
data class SportSignFilterActions(
    val onSelectSports: (Set<SectionName>) -> Unit = {},
    val onSelectBuilding: (String?) -> Unit = {},
    val onSelectTeacher: (String?) -> Unit = {},
    val onSelectTime: (String?) -> Unit = {},
    val onShowOnlyAvailable: (Boolean) -> Unit = {},
    val onShowAutoSign: (Boolean) -> Unit = {},
    val onShowOnlyFriends: (Boolean) -> Unit = {},
    val onReset: () -> Unit = {},
)

object SportSignFiltersTestTags {
    const val SPORT = "sport_filters_sport"
    const val BUILDING = "sport_filters_building"
    const val TEACHER = "sport_filters_teacher"
    const val TIME = "sport_filters_time"
    const val CHIPS = "sport_filters_chips"
    const val AVAILABLE = "sport_filters_available"
    const val AUTO_SIGN = "sport_filters_auto_sign"
    const val FRIENDS = "sport_filters_friends"
    const val RESET = "sport_filters_reset"
}

/**
 * The `Запись` filters header (`item_sport_filters_header` without the week strip): the section field that opens
 * [SportSectionPickerDialog], the building, teacher and time dropdowns (teacher and time hidden by
 * [SportSignUiState.Content.hideTeacherSelector] and [SportSignUiState.Content.hideTimeSelector]), then a chip row
 * with the three toggles and, while [SportSignUiState.Content.hasActiveFilters], the reset chip. The chip row scrolls
 * horizontally and hands a swipe at its edge over to the tab pager.
 */
@Composable
fun SportSignFilters(
    state: SportSignUiState.Content,
    actions: SportSignFilterActions,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        SectionField(state.selectedSportNames, onClick = { pickerOpen = true })
        FilterDropdown(
            label = stringResource(Res.string.sport_filter_building),
            anyLabel = stringResource(Res.string.sport_any_building),
            options = state.availableBuildings,
            selected = state.selectedBuildingName,
            onSelect = actions.onSelectBuilding,
            tag = SportSignFiltersTestTags.BUILDING,
        )
        if (!state.hideTeacherSelector) {
            FilterDropdown(
                label = stringResource(Res.string.sport_filter_teacher),
                anyLabel = stringResource(Res.string.sport_any_teacher),
                options = state.availableTeachers,
                selected = state.selectedTeacherName,
                onSelect = actions.onSelectTeacher,
                tag = SportSignFiltersTestTags.TEACHER,
            )
        }
        if (!state.hideTimeSelector) {
            FilterDropdown(
                label = stringResource(Res.string.sport_filter_time),
                anyLabel = stringResource(Res.string.sport_any_time),
                options = state.availableTimeSlots,
                selected = state.selectedTimeSlot,
                onSelect = actions.onSelectTime,
                tag = SportSignFiltersTestTags.TIME,
            )
        }
        FilterChips(state, actions)
    }
    if (pickerOpen) {
        SportSectionPickerDialog(
            available = state.availableSports,
            selected = state.selectedSportNames,
            used = state.usedSportNames,
            onConfirm = { names ->
                pickerOpen = false
                actions.onSelectSports(names)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/** The read-only section field: the selected short names, the label inside while none is selected. */
@Composable
private fun SectionField(selected: Set<SectionName>, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    FilterField(
        value = selected.joinToString(", ") { it.shorten() },
        label = stringResource(Res.string.sport_filter_type),
        textStyle = ItmoTheme.typography.bodyLarge,
        interactionSource = interactionSource,
        trailingIcon = { Icon(painterResource(KitRes.drawable.ic_search), contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = stringResource(Res.string.sport_filter_select_type),
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(SportSignFiltersTestTags.SPORT),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    anyLabel: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    tag: String,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        FilterField(
            value = selected ?: anyLabel,
            label = label,
            textStyle = ItmoTheme.typography.bodyMedium,
            interactionSource = remember { MutableInteractionSource() },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .testTag(tag),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf<String?>(null) + options).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option ?: anyLabel) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * An outlined field that only shows a value: the dense `TextInputLayout` box (16 dp corners, the label inside while
 * the value is empty), at least a touch target high. A long value wraps instead of being cut.
 */
@Composable
private fun FilterField(
    value: String,
    label: String,
    textStyle: TextStyle,
    interactionSource: MutableInteractionSource,
    trailingIcon: @Composable () -> Unit,
    modifier: Modifier,
) {
    val shape = ItmoTheme.shapes.large
    // The decoration box sizes its outline to its constraints' minimum: passed down, the field spans the width.
    Box(modifier.heightIn(min = ItmoTheme.spacing.touchTarget), propagateMinConstraints = true) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = { Text(value, color = ItmoTheme.colorScheme.onSurface, style = textStyle) },
            enabled = true,
            singleLine = false,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            label = { Text(label) },
            trailingIcon = trailingIcon,
            contentPadding = OutlinedTextFieldDefaults.contentPaddingWithLabel(
                top = ItmoTheme.spacing.content,
                bottom = ItmoTheme.spacing.content,
            ),
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    shape = shape,
                )
            },
        )
    }
}

@Composable
private fun FilterChips(state: SportSignUiState.Content, actions: SportSignFilterActions) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .tabSwipeHandover(scroll)
            .horizontalScroll(scroll)
            .testTag(SportSignFiltersTestTags.CHIPS),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        ToggleChip(
            stringResource(Res.string.sport_filter_available),
            state.showOnlyAvailable,
            actions.onShowOnlyAvailable,
            SportSignFiltersTestTags.AVAILABLE,
        )
        ToggleChip(
            stringResource(Res.string.sport_filter_auto_sign),
            state.showAutoSign,
            actions.onShowAutoSign,
            SportSignFiltersTestTags.AUTO_SIGN,
        )
        ToggleChip(
            stringResource(Res.string.sport_filter_friends),
            state.showOnlyFriends,
            actions.onShowOnlyFriends,
            SportSignFiltersTestTags.FRIENDS,
        )
        if (state.hasActiveFilters) {
            AssistChip(
                onClick = actions.onReset,
                label = { Text(stringResource(Res.string.common_reset)) },
                modifier = Modifier.testTag(SportSignFiltersTestTags.RESET),
                leadingIcon = {
                    Icon(
                        painterResource(KitRes.drawable.ic_close),
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                        tint = ItmoTheme.colorScheme.onSurface,
                    )
                },
            )
        }
    }
}

/** `Widget.Material3.Chip.Filter`: a check while selected. */
@Composable
private fun ToggleChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label) },
        modifier = Modifier.testTag(tag),
        leadingIcon = if (selected) {
            {
                Icon(
                    painterResource(KitRes.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}
