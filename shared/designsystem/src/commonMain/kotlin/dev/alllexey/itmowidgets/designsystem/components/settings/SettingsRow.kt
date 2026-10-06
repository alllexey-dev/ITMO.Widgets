package dev.alllexey.itmowidgets.designsystem.components.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoSwitch
import dev.alllexey.itmowidgets.designsystem.components.groups.IosCheckmark
import dev.alllexey.itmowidgets.designsystem.components.groups.IosDisclosure
import dev.alllexey.itmowidgets.designsystem.components.groups.IosListRow
import dev.alllexey.itmowidgets.designsystem.platform.ItmoHapticEvent
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.platform.rememberItmoHaptics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import kotlin.math.max
import org.jetbrains.compose.resources.painterResource

/**
 * A settings row: [title] in `bodyLarge`, an optional [description] and [value] in `bodyMedium` below it, and an
 * optional [trailing] element. With [onClick] the whole row is the target; a disabled row is dimmed and inert. The
 * height and padding follow the [SettingsGroup]'s density.
 *
 * Under the iOS style every density takes a cell of an inset group: at least `IosMetrics.rowMinHeight` with UIKit's
 * margins, the title in body, the description in subheadline `secondaryLabel` below it and the [value] in body
 * `secondaryLabel` at the end of the title's line (below the title when both do not fit, as `valueCell()` does), the
 * pressed cell instead of a ripple.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = if (onClick == null) {
        Modifier.semantics(mergeDescendants = true) {}
    } else {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    }
    SettingsRowLayout(title, description, value, enabled, modifier.then(interaction), trailing)
}

/**
 * A switch over the whole row, read as one switch. [checked] is null while an asynchronous source has not provided
 * the real value: the switch keeps its place but stays hidden, so a temporary `false` never flips to `true` on screen.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val interactive = enabled && checked != null
    val haptics = rememberItmoHaptics()
    val ios = ItmoTheme.platformStyle == ItmoPlatformStyle.Ios
    val interaction = if (checked == null) {
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            disabled()
        }
    } else {
        Modifier.toggleable(checked, enabled = enabled, role = Role.Switch) {
            if (ios) haptics.perform(ItmoHapticEvent.Toggle)
            onCheckedChange(it)
        }
    }
    SettingsRowLayout(title, description, value = null, interactive, modifier.then(interaction)) {
        ItmoSwitch(
            checked = checked == true,
            onCheckedChange = null,
            modifier = Modifier
                .alpha(if (checked == null) 0f else 1f)
                .then(if (ios) Modifier.padding(start = IosMetrics.accessoryGap) else Modifier),
            enabled = interactive,
        )
    }
}

/**
 * A single choice made elsewhere, in a dialog or a sheet: the row shows the current [value] (null while it is
 * loading) and a chevron, and [onClick] opens the options.
 */
@Composable
fun SettingsChoiceRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    SettingsRow(title, modifier, description, value, enabled, onClick) { Chevron() }
}

/**
 * Opens another screen. [value] only for a section that honestly collapses into one state (services on or off); a
 * section of independent options says what is inside through [description] instead.
 */
@Composable
fun SettingsNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    enabled: Boolean = true,
) {
    SettingsRow(title, modifier, description, value, enabled, onClick) { Chevron() }
}

/** A read-only fact, such as the application version: [title] over [value], read as one item, not a target. */
@Composable
fun SettingsInfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    SettingsRow(title, modifier, value = value)
}

/**
 * Runs a command without leaving the screen (copy, sign out), with an optional decorative [trailingIcon] that says
 * what happens; the row's text is its label.
 */
@Composable
fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    trailingIcon: Painter? = null,
    enabled: Boolean = true,
) {
    val trailing: (@Composable () -> Unit)? = trailingIcon?.let { icon -> { TrailingIcon(icon) } }
    SettingsRow(title, modifier, description, value, enabled, onClick, trailing)
}

/** Whether a [SettingsSelectionRow] is one option of several or one of a set the user can pick many of. */
enum class SelectionMode {
    /** Read as a radio button. */
    Single,

    /** Read as a checkbox. */
    Multiple,
}

/**
 * One option of a list the user picks from (port of `core/ui/SelectionAccessibility.kt`): the whole row is the
 * target, a check in `primary` shows the selection, and TalkBack reads the row once with its selected state. A closed
 * row ([onSelect] null, a private profile) is neither a target nor selected and never shows the check. Under the iOS
 * style the check is UIKit's checkmark accessory in the tint, and a pick plays the selection haptic (a single
 * choice) or the toggle one (a multiple choice).
 */
@Composable
fun SettingsSelectionRow(
    title: String,
    selected: Boolean,
    onSelect: (() -> Unit)?,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    mode: SelectionMode = SelectionMode.Single,
) {
    val pick = rememberSelectionFeedback(onSelect, mode)
    val interaction = when {
        pick == null -> Modifier.semantics(mergeDescendants = true) {}
        mode == SelectionMode.Single -> Modifier.selectable(selected, role = Role.RadioButton, onClick = pick)
        else -> Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { pick() })
    }
    val trailing: (@Composable () -> Unit)? = when {
        onSelect == null -> null
        ItmoTheme.platformStyle == ItmoPlatformStyle.Ios -> {
            { IosCheckmark(selected) }
        }
        else -> {
            {
                TrailingIcon(
                    painterResource(Res.drawable.ic_check),
                    Modifier.alpha(if (selected) 1f else 0f),
                    tint = ItmoTheme.colorScheme.primary,
                )
            }
        }
    }
    SettingsRowLayout(title, description, value, enabled = true, modifier.then(interaction), trailing)
}

/**
 * [onSelect] with the iOS haptic of a pick: [ItmoHapticEvent.Selection] for one of several options,
 * [ItmoHapticEvent.Toggle] for one of a set; [onSelect] itself under Material, null for a closed row. Shared with the
 * selection rows of people.
 */
@Composable
internal fun rememberSelectionFeedback(onSelect: (() -> Unit)?, mode: SelectionMode): (() -> Unit)? {
    if (onSelect == null || ItmoTheme.platformStyle == ItmoPlatformStyle.Material) return onSelect
    val haptics = rememberItmoHaptics()
    val event = if (mode == SelectionMode.Single) ItmoHapticEvent.Selection else ItmoHapticEvent.Toggle
    return {
        haptics.perform(event)
        onSelect()
    }
}

@Composable
private fun SettingsRowLayout(
    title: String,
    description: String?,
    value: String?,
    enabled: Boolean,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        IosSettingsRowLayout(title, description, value, enabled, modifier, trailing)
        return
    }
    val metrics = LocalSettingsDensity.current.metrics()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = ItmoTheme.spacing.group, vertical = metrics.verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = metrics.textEnd)) {
            Text(title, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
            listOfNotNull(description, value).forEach { line ->
                Text(
                    line,
                    Modifier.padding(top = ItmoTheme.spacing.related),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodyMedium,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * A cell of an inset group: the title and its description, the value at the end of the title's line or below it,
 * then [trailing] (an accessory or a switch) before the trailing margin.
 */
@Composable
private fun IosSettingsRowLayout(
    title: String,
    description: String?,
    value: String?,
    enabled: Boolean,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)?,
) {
    val colors = ItmoTheme.iosColors
    IosListRow(separatorInset = IosMetrics.separatorInset) {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = IosMetrics.rowMinHeight)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .padding(horizontal = IosMetrics.rowHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IosTitleAndValue(
                title = {
                    Column {
                        Text(title, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
                        if (description != null) {
                            Text(description, color = colors.secondaryLabel, style = ItmoTheme.typography.bodyMedium)
                        }
                    }
                },
                value = value?.let { text ->
                    { Text(text, color = colors.secondaryLabel, style = ItmoTheme.typography.bodyLarge) }
                },
                modifier = Modifier.weight(1f).padding(vertical = IosMetrics.rowVerticalPadding),
            )
            trailing?.invoke()
        }
    }
}

/**
 * `valueCell()`'s layout: the [value] at the end of the [title]'s first line when both fit side by side with
 * [IosMetrics.accessoryGap] between them, else below the title, [ValueStackGap] apart, as UIKit stacks them.
 */
@Composable
private fun IosTitleAndValue(
    title: @Composable () -> Unit,
    value: (@Composable () -> Unit)?,
    modifier: Modifier,
) {
    if (value == null) {
        Box(modifier) { title() }
        return
    }
    Layout(contents = listOf(title, value), modifier = modifier) { (titles, values), constraints ->
        val titleItem = titles.single()
        val valueItem = values.single()
        val width = constraints.maxWidth
        val gap = IosMetrics.accessoryGap.roundToPx()
        val titleWidth = titleItem.maxIntrinsicWidth(Constraints.Infinity)
        val valueWidth = valueItem.maxIntrinsicWidth(Constraints.Infinity)
        if (titleWidth + gap + valueWidth <= width) {
            val valuePlaceable = valueItem.measure(Constraints(maxWidth = valueWidth))
            val titlePlaceable = titleItem.measure(Constraints(maxWidth = width - gap - valuePlaceable.width))
            val height = max(titlePlaceable.height, valuePlaceable.height).coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                titlePlaceable.placeRelative(0, 0)
                valuePlaceable.placeRelative(width - valuePlaceable.width, 0)
            }
        } else {
            val titlePlaceable = titleItem.measure(Constraints(maxWidth = width))
            val valuePlaceable = valueItem.measure(Constraints(maxWidth = width))
            val top = titlePlaceable.height + ValueStackGap.roundToPx()
            val height = (top + valuePlaceable.height).coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                titlePlaceable.placeRelative(0, 0)
                valuePlaceable.placeRelative(0, top)
            }
        }
    }
}

@Composable
private fun Chevron() {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        IosDisclosure()
    } else {
        TrailingIcon(painterResource(Res.drawable.ic_chevron_right))
    }
}

@Composable
private fun TrailingIcon(
    icon: Painter,
    modifier: Modifier = Modifier,
    tint: Color = ItmoTheme.colorScheme.onSurfaceVariant,
) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        val iosModifier = modifier.padding(start = IosMetrics.accessoryGap).size(TrailingIconSize)
        Icon(icon, contentDescription = null, modifier = iosModifier, tint = ItmoTheme.iosColors.secondaryLabel)
        return
    }
    Icon(icon, contentDescription = null, modifier = modifier.size(TrailingIconSize), tint = tint)
}

private class RowMetrics(val minHeight: Dp, val verticalPadding: Dp, val textEnd: Dp)

@Composable
private fun SettingsDensity.metrics(): RowMetrics = when (this) {
    SettingsDensity.Compact -> RowMetrics(
        minHeight = ItmoTheme.spacing.touchTarget,
        verticalPadding = ItmoTheme.spacing.compact,
        textEnd = ItmoTheme.spacing.content,
    )
    SettingsDensity.Default -> RowMetrics(
        minHeight = DefaultRowMinHeight,
        verticalPadding = ItmoTheme.spacing.content,
        textEnd = ItmoTheme.spacing.group,
    )
}

/** `Widget.ItmoWidgets.SettingsRow`'s `android:minHeight`. */
private val DefaultRowMinHeight = 56.dp

/** The 24 dp chevron and check of `item_setting_row.xml` and `item_sheet_scores_option.xml`. */
private val TrailingIconSize = 24.dp

/** `valueCell()`'s `textToSecondaryTextVerticalPadding` when the value moves below the title. */
private val ValueStackGap = 4.dp

/** `SettingsRenderer.DISABLED_ALPHA`. */
private const val DISABLED_ALPHA = 0.6f
