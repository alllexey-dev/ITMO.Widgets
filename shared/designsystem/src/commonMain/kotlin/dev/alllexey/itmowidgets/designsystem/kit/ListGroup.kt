package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.presentation.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupSurface
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import org.jetbrains.compose.resources.painterResource

/** Collects the rows of a [ListGroup]. */
class ListGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    /** One row; usually a [ListRow] or a [SwitchListRow]. */
    fun item(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * A connected group of rows (M3 Expressive's segmented list): one surface, rows 2 dp apart, 20 dp outer and 4 dp inner
 * corners, one step stronger inside a sheet. Sets of navigation rows and settings go here, never loose cards.
 */
@Composable
fun ListGroup(modifier: Modifier = Modifier, build: ListGroupScope.() -> Unit) {
    val rows = ListGroupScope().apply(build).rows
    val surface = if (LocalOnSheet.current) GroupSurface.Sheet else GroupSurface.Screen
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            Box(Modifier.fillMaxWidth().connectedGroupItem(GroupPosition.of(index, rows.size), surface)) { row() }
        }
    }
}

/**
 * A list row: an optional [icon] (in a tile when [iconTone] is set) or a custom [leading] such as an avatar, the
 * [title] in `bodyLarge` over an optional [subtitle] and any lines [below] it, then a [badge] count, a [trailing] value,
 * an [accessory] (a check, a lock, a small button) and a chevron when [chevron]. [danger] turns the title and icon to
 * the error colour (`Удалить аккаунт`). With [onClick] the whole row is the target.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Painter? = null,
    iconTone: Tone? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: String? = null,
    badge: Int? = null,
    accessory: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = false,
    danger: Boolean = false,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        if (leading != null) leading() else RowIcon(icon, iconTone, danger)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (danger) ItmoTheme.colorScheme.error else ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(subtitle, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            }
            if (below != null) {
                Column(Modifier.padding(top = ItmoTheme.spacing.related), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related), content = below)
            }
        }
        if (badge != null && badge > 0) Badge { Text("$badge") }
        if (trailing != null) {
            // Capped, so a long value (an ITMO.ID session label) wraps instead of squeezing the title to a letter per line.
            Text(
                trailing,
                Modifier.widthIn(max = TrailingMaxWidth),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
            )
        }
        accessory?.invoke(this)
        if (chevron) {
            Icon(
                painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = ItmoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A row with a trailing switch (a check in the thumb when on); the whole row toggles. [inset] drops the side padding
 * and the minimum height of a group row, for a switch placed straight inside a card.
 */
@Composable
fun SwitchListRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Painter? = null,
    iconTone: Tone? = null,
    enabled: Boolean = true,
    inset: Boolean = false,
) {
    val sides = if (inset) 0.dp else ItmoTheme.spacing.cardPadding
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(checked, enabled, Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = if (inset) ItmoTheme.spacing.touchTarget else RowMinHeight)
            .padding(start = sides, end = if (inset) 0.dp else ItmoTheme.spacing.content)
            .padding(vertical = if (inset) ItmoTheme.spacing.related else ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        if (icon != null) Box(Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)) { RowIcon(icon, iconTone, false) }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) ItmoTheme.colorScheme.onSurface else ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(subtitle, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            thumbContent = if (checked) {
                { Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
    }
}

/** A quiet caption under a group or a card that its rows share. */
@Composable
fun ListGroupNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(horizontal = ItmoTheme.spacing.cardPadding).padding(top = NoteTopPadding),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodySmall,
    )
}

@Composable
private fun RowIcon(icon: Painter?, tone: Tone?, danger: Boolean) {
    when {
        icon == null -> Unit
        tone != null -> IconTile(icon, if (danger) Tone.Error else tone)
        else -> Icon(
            icon,
            contentDescription = null,
            tint = if (danger) ItmoTheme.colorScheme.error else ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A group row is at least this tall (design.md "Connected groups"). */
private val RowMinHeight = 56.dp
private val TrailingMaxWidth = 160.dp
private val NoteTopPadding = 2.dp
private const val DISABLED_ALPHA = 0.38f

