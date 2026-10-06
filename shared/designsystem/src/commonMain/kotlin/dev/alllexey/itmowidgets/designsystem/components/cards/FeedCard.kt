package dev.alllexey.itmowidgets.designsystem.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.buttons.Pill
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import org.jetbrains.compose.resources.painterResource

/**
 * A card of a feed (`Card.Content`): `surfaceContainerLow` in the content corners, 16 dp from the screen's sides and
 * 4 dp from its neighbours. [onClick] makes the whole card clickable; [outlined] adds a 1 dp `outlineVariant` stroke
 * (the home feed's hints).
 */
@Composable
fun FeedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    outlined: Boolean = false,
    content: @Composable () -> Unit,
) {
    val outer = modifier
        .fillMaxWidth()
        .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.related)
    val shape = ItmoTheme.shapes.cardContent
    val color = ItmoTheme.colorScheme.surfaceContainerLow
    val border = if (outlined) BorderStroke(CardStroke, ItmoTheme.colorScheme.outlineVariant) else null
    if (onClick == null) {
        Surface(outer, shape = shape, color = color, border = border, content = content)
    } else {
        Surface(onClick, outer, shape = shape, color = color, border = border, content = content)
    }
}

/** A [FeedCard]'s title line: a 20 dp `primary` icon, the title in `titleMedium` and [trailing] at the end. */
@Composable
fun FeedCardHeader(
    icon: Painter,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(HeaderIconSize),
            tint = ItmoTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(ItmoTheme.spacing.compact))
        Text(
            title,
            Modifier.weight(1f),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleMedium,
        )
        trailing()
    }
}

/**
 * A feed row's label (`сейчас`, `ждём запись`): the neutral [Pill] colours in `labelSmall`, and `primary` for the item
 * in progress ([focused]), which [Pill] has no variant for.
 */
@Composable
fun FeedRowBadge(text: String, modifier: Modifier = Modifier, focused: Boolean = false) {
    val colors = ItmoTheme.colorScheme
    Text(
        text,
        modifier
            .clip(BadgeShape)
            .background(if (focused) colors.primary else colors.secondaryContainer)
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = BadgePaddingVertical),
        color = if (focused) colors.onPrimary else colors.onSecondaryContainer,
        style = ItmoTheme.typography.labelSmall,
        maxLines = 1,
    )
}

/** The 48 dp close button of a dismissible [FeedCard]; [label] is what TalkBack reads. */
@Composable
fun FeedCloseButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick, modifier.size(ItmoTheme.spacing.touchTarget)) {
        Icon(painterResource(Res.drawable.ic_close), label, tint = ItmoTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A [FeedCard] that opens on a click and is marked read with its close button: a header with [count] in a [Pill],
 * then [body], which wraps without truncation. TalkBack reads [description] for the card and [dismissLabel] for the
 * button; [closeModifier] goes to the button (a test tag).
 */
@Composable
fun ClosableFeedCard(
    icon: Painter,
    title: String,
    count: Int,
    body: String,
    description: String,
    dismissLabel: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    closeModifier: Modifier = Modifier,
) {
    FeedCard(modifier.semantics { contentDescription = description }, onClick = onOpen) {
        Column(
            Modifier.padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.related,
                end = ItmoTheme.spacing.related,
                bottom = ItmoTheme.spacing.cardPadding,
            ),
        ) {
            FeedCardHeader(icon, title, Modifier.clearAndSetSemantics {}) {
                Pill(count.toString(), Modifier.padding(start = ItmoTheme.spacing.compact))
                Spacer(Modifier.size(ItmoTheme.spacing.touchTarget))
            }
            Text(
                body,
                Modifier.padding(end = ItmoTheme.spacing.content).clearAndSetSemantics {},
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
        // Over the header's placeholder, outside its cleared semantics, so TalkBack still finds it.
        Box(Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.related, end = ItmoTheme.spacing.related)) {
            FeedCloseButton(dismissLabel, onDismiss, closeModifier.align(Alignment.TopEnd))
        }
    }
}

private val HeaderIconSize = 20.dp
private val CardStroke = 1.dp
private val BadgeShape = RoundedCornerShape(8.dp)
private val BadgePaddingVertical = 2.dp
