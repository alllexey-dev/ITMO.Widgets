package dev.alllexey.itmowidgets.designsystem.components.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import org.jetbrains.compose.resources.painterResource

/**
 * A settings row: [title] in `bodyLarge`, an optional [description] and [value] in `bodyMedium` below it, and an
 * optional [trailing] element. With [onClick] the whole row is the target; a disabled row is dimmed and inert. The
 * height and padding follow the [SettingsGroup]'s density.
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
    val interaction = if (checked == null) {
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            disabled()
        }
    } else {
        Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    }
    SettingsRowLayout(title, description, value = null, interactive, modifier.then(interaction)) {
        Switch(
            checked = checked == true,
            onCheckedChange = null,
            modifier = Modifier.alpha(if (checked == null) 0f else 1f),
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
 * row ([onSelect] null, a private profile) is neither a target nor selected and never shows the check.
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
    val interaction = when {
        onSelect == null -> Modifier.semantics(mergeDescendants = true) {}
        mode == SelectionMode.Single -> Modifier.selectable(selected, role = Role.RadioButton, onClick = onSelect)
        else -> Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { onSelect() })
    }
    val trailing: (@Composable () -> Unit)? = if (onSelect == null) {
        null
    } else {
        {
            TrailingIcon(
                painterResource(Res.drawable.ic_check),
                Modifier.alpha(if (selected) 1f else 0f),
                tint = ItmoTheme.colorScheme.primary,
            )
        }
    }
    SettingsRowLayout(title, description, value, enabled = true, modifier.then(interaction), trailing)
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

@Composable
private fun Chevron() = TrailingIcon(painterResource(Res.drawable.ic_chevron_right))

@Composable
private fun TrailingIcon(
    icon: Painter,
    modifier: Modifier = Modifier,
    tint: Color = ItmoTheme.colorScheme.onSurfaceVariant,
) {
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

/** `SettingsRenderer.DISABLED_ALPHA`. */
private const val DISABLED_ALPHA = 0.6f
