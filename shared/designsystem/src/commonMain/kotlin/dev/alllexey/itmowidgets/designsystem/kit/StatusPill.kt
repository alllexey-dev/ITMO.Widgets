package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** One pill: [text] in its [tone], an optional leading [icon]; [solid] fills it with `primary` (`идёт` on a hero). */
@Immutable
data class PillSpec(val text: String, val tone: Tone = Tone.Neutral, val icon: Painter? = null, val solid: Boolean = false)

/**
 * A short status on its tone's container: `идёт`, `далее`, `изменена`, `отменена`, `очередь 2 из 5`, `записаны`. The
 * text carries the meaning; no `·` inside a pill.
 */
@Composable
fun StatusPill(pill: PillSpec, modifier: Modifier = Modifier) {
    val background = if (pill.solid) ItmoTheme.colorScheme.primary else pill.tone.container()
    val foreground = if (pill.solid) ItmoTheme.colorScheme.onPrimary else pill.tone.onContainer()
    Row(
        modifier
            .background(background, ItmoTheme.shapes.full)
            .padding(horizontal = PillHorizontalPadding, vertical = PillVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
    ) {
        pill.icon?.let { Icon(it, contentDescription = null, Modifier.size(PillIconSize), tint = foreground) }
        Text(pill.text, color = foreground, style = ItmoTheme.typography.labelMedium, maxLines = 1)
    }
}

/** A convenience for one pill without a [PillSpec]. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Neutral, icon: Painter? = null) =
    StatusPill(PillSpec(text, tone, icon), modifier)

private val PillHorizontalPadding = 10.dp
private val PillVerticalPadding = 3.dp
private val PillIconSize = 14.dp
