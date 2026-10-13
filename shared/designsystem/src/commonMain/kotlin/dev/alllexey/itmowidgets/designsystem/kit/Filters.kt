package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_search
import org.jetbrains.compose.resources.painterResource

/** One filter chip: [label], an optional quiet [count] after it, [selected] with a check. */
@Immutable
data class ChipSpec(val label: String, val selected: Boolean, val count: Int? = null)

/**
 * Filter chips (the one pattern for filters): a row that scrolls sideways before the tab swipe takes over, or, with [wrap],
 * lines that wrap within the margins (a filter set in a sheet). [onClick] gets the chip's index.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterChips(chips: List<ChipSpec>, onClick: (Int) -> Unit, modifier: Modifier = Modifier, wrap: Boolean = false) {
    val content: @Composable () -> Unit = {
        chips.forEachIndexed { index, chip ->
            FilterChip(
                selected = chip.selected,
                onClick = { onClick(index) },
                label = {
                    Text(chip.label, maxLines = 1)
                    if (chip.count != null) {
                        Text(
                            "${chip.count}",
                            Modifier.padding(start = ItmoTheme.spacing.compact),
                            color = ItmoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = if (chip.selected) {
                    { Icon(painterResource(Res.drawable.ic_check), contentDescription = null, Modifier.size(ChipIconSize)) }
                } else {
                    null
                },
            )
        }
    }
    if (wrap) {
        FlowRow(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
        ) { content() }
    } else {
        val scroll = rememberScrollState()
        Row(
            modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
        ) { content() }
    }
}

/**
 * The search of a list as a pill: the search icon, the [query] or the [placeholder], a clear button while there is a
 * query and an optional [action] at the end (more filters) with a [badge] of what it narrows. [clearLabel] and
 * [actionLabel] are what TalkBack reads.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
    action: Painter? = null,
    actionLabel: String? = null,
    badge: Int = 0,
    onAction: () -> Unit = {},
) {
    val colors = ItmoTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SearchHeight)
            .background(colors.surfaceContainerHigh, ItmoTheme.shapes.full)
            .padding(start = ItmoTheme.spacing.group, end = ItmoTheme.spacing.related),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        Icon(painterResource(Res.drawable.ic_search), contentDescription = null, tint = colors.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    placeholder,
                    color = colors.onSurfaceVariant,
                    style = ItmoTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                singleLine = true,
                textStyle = ItmoTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(painterResource(Res.drawable.ic_close), contentDescription = clearLabel, tint = colors.onSurfaceVariant)
            }
        }
        if (action != null) {
            IconButton(onClick = onAction) {
                if (badge > 0) {
                    BadgedBox(badge = { Badge { Text("$badge") } }) { Icon(action, contentDescription = actionLabel) }
                } else {
                    Icon(action, contentDescription = actionLabel)
                }
            }
        }
    }
}

/** Places taken: a short bar filled to [share] and [text] (`22 из 24`), both in [tone]. */
@Composable
fun OccupancyBar(text: String, share: Float, tone: Tone, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(OccupancyWidth).heightIn(min = OccupancyHeight).background(ItmoTheme.colorScheme.surfaceContainerHighest, ItmoTheme.shapes.full)) {
            Box(Modifier.fillMaxHeight().heightIn(min = OccupancyHeight).fillMaxWidth(share.coerceIn(0f, 1f)).background(tone.ink(), ItmoTheme.shapes.full))
        }
        Text(text, color = tone.ink(), style = ItmoTheme.typography.labelMedium, maxLines = 1)
    }
}

private val ChipIconSize = 18.dp
private val SearchHeight = 56.dp
private val OccupancyWidth = 44.dp
private val OccupancyHeight = 6.dp
