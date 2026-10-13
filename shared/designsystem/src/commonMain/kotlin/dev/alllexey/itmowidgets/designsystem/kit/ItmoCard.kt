package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import org.jetbrains.compose.resources.painterResource

/**
 * A card with one topic (`Card.Content`, 20 dp, no stroke): [cardSurface] by default, a [tone]'s container to stand
 * out. [hero] is the one hero of a screen (`Card.Hero`, 28 dp, 20 dp padding); at most one per screen.
 */
@Composable
fun ItmoCard(
    modifier: Modifier = Modifier,
    tone: Tone? = null,
    hero: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = if (hero) ItmoTheme.shapes.cardHero else ItmoTheme.shapes.cardContent
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = tone?.container() ?: cardSurface(),
        contentColor = if (tone == null || tone == Tone.Neutral) ItmoTheme.colorScheme.onSurface else tone.onContainer(),
    ) {
        Column(
            Modifier.padding(if (hero) ItmoTheme.spacing.summaryPadding else ItmoTheme.spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(CardContentGap),
            content = content,
        )
    }
}

/** A rounded square with an icon in a tone, for rows and notices. */
@Composable
fun IconTile(icon: Painter, tone: Tone, modifier: Modifier = Modifier, size: Dp = IconTileSize) {
    Box(
        modifier.size(size).clip(ItmoTheme.shapes.medium).background(tone.container()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(size * ICON_SHARE), tint = tone.onContainer())
    }
}

/** A rounded square with a short text in a tone (a mark's `+9`), the same size as an [IconTile]. */
@Composable
fun TextTile(text: String, tone: Tone, modifier: Modifier = Modifier, size: Dp = IconTileSize) {
    Box(
        modifier.size(size).clip(ItmoTheme.shapes.medium).background(tone.container()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = tone.onContainer(),
            style = ItmoTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Something here is locked for the viewer, with a fix they can do: an icon tile, what is unavailable, why, and one
 * tonal [fix] (`Подключить`). It stands where the missing content would be; never a silent hide.
 */
@Composable
fun GateCard(
    title: String,
    reason: String,
    fix: String,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter = painterResource(Res.drawable.ic_lock),
) = NoticeCard(title, reason, icon, Tone.Warning, NoticeAction(fix, onFix), modifier)

/** Not a lock but something worth doing (`Добавить ссылку`): the same layout as [GateCard] with its [action]. */
@Composable
fun ActionCard(
    title: String,
    reason: String,
    action: String,
    onAction: () -> Unit,
    icon: Painter,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Primary,
) = NoticeCard(title, reason, icon, tone, NoticeAction(action, onAction), modifier)

/**
 * Locked with no fix for the viewer: what is closed and why, no button. Not for someone else's privacy, where hidden
 * data is simply absent.
 */
@Composable
fun LockNote(
    title: String,
    reason: String,
    modifier: Modifier = Modifier,
    icon: Painter = painterResource(Res.drawable.ic_lock),
) = NoticeCard(title, reason, icon, Tone.Neutral, null, modifier)

private class NoticeAction(val label: String, val onClick: () -> Unit)

@Composable
private fun NoticeCard(title: String, reason: String, icon: Painter, tone: Tone, action: NoticeAction?, modifier: Modifier) {
    ItmoCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(RowGap), verticalAlignment = Alignment.Top) {
            IconTile(icon, tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
                Text(title, style = ItmoTheme.typography.titleMedium)
                Text(reason, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
                if (action != null) {
                    FilledTonalButton(onClick = action.onClick, Modifier.padding(top = ItmoTheme.spacing.compact)) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

/**
 * Old data kept visible, offline or under a gate: [label] (`Данные на 11:44`) over the [content], dimmed so it does
 * not read as current.
 */
@Composable
fun StaleContent(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CardContentGap)) {
        Text(
            label,
            Modifier.padding(start = ItmoTheme.spacing.related),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.labelMedium,
        )
        Box(Modifier.alpha(STALE_ALPHA)) { content() }
    }
}

/** Between the lines of a card. */
private val CardContentGap = 6.dp

/** Between a tile and its text. */
internal val RowGap = 14.dp

internal val IconTileSize = 40.dp
private const val ICON_SHARE = 0.55f
private const val STALE_ALPHA = 0.55f
