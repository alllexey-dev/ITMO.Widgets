package dev.alllexey.itmowidgets.designsystem.components.rows

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.buttons.Pill
import dev.alllexey.itmowidgets.designsystem.components.groups.IosListRow
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics

/**
 * A link in a connected group (port of `item_subject_link.xml` and `core/ui/SubjectLinkRow.kt`): an optional
 * decorative category [icon], the [title] in `bodyLarge` that wraps with hyphens,
 * an optional [caption] on a second line, then the [ownBadge] (`моя`) of the viewer's own link or the [votes] of
 * another student's, usually a [VotePill]. Own and others' links share the style; only the badge tells them apart.
 *
 * The whole row opens the link; [onLongClick] opens its actions, named for TalkBack by [longClickLabel]. TalkBack
 * reads the row once: title, caption, badge and score, with the pill's votes as custom actions. Pass
 * `Modifier.connectedGroupItem(...)` in [modifier] so the ripple keeps the row's shape.
 *
 * Under the iOS style it is a cell of an inset group: the icon in the tint, UIKit's row height and margins, the
 * caption in subheadline `secondaryLabel`, the separator under it inset to the title, the pressed cell instead of a
 * ripple. The badge and the pill keep their geometry.
 */
@Composable
fun LinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    caption: String? = null,
    ownBadge: String? = null,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    votes: (@Composable RowScope.() -> Unit)? = null,
) {
    val trailing = ownBadge != null || votes != null
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        val separatorInset = if (icon != null) IosMetrics.separatorInsetWithIcon else IosMetrics.separatorInset
        IosListRow(separatorInset) {
            IosLinkRowLayout(title, onClick, modifier, icon, caption, ownBadge, onLongClick, longClickLabel, votes)
        }
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = LinkRowMinHeight)
            .combinedClickable(onLongClickLabel = longClickLabel, onLongClick = onLongClick, onClick = onClick)
            .padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.compact,
                // Without a trailing element the text reaches the row's 16 dp content edge.
                end = if (trailing) ItmoTheme.spacing.related else ItmoTheme.spacing.cardPadding,
                bottom = ItmoTheme.spacing.compact,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(end = ItmoTheme.spacing.cardPadding).size(IconSize),
                tint = ItmoTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(end = if (trailing) ItmoTheme.spacing.compact else 0.dp)
                .padding(vertical = ItmoTheme.spacing.related),
        ) {
            Text(title, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge.wrapping())
            if (!caption.isNullOrEmpty()) {
                Text(
                    caption,
                    Modifier.padding(top = CaptionGap),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodyMedium,
                )
            }
        }
        if (ownBadge != null) {
            Pill(ownBadge, Modifier.padding(end = ItmoTheme.spacing.content))
        }
        votes?.invoke(this)
    }
}

@Composable
private fun IosLinkRowLayout(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: Painter?,
    caption: String?,
    ownBadge: String?,
    onLongClick: (() -> Unit)?,
    longClickLabel: String?,
    votes: (@Composable RowScope.() -> Unit)?,
) {
    val trailing = ownBadge != null || votes != null
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = IosMetrics.rowMinHeight)
            .combinedClickable(onLongClickLabel = longClickLabel, onLongClick = onLongClick, onClick = onClick)
            .padding(
                start = IosMetrics.rowHorizontalPadding,
                // The pill's own inset keeps its arrows clear of the edge, as in the Material row.
                end = if (trailing) ItmoTheme.spacing.related else IosMetrics.rowHorizontalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(end = IosMetrics.rowHorizontalPadding).size(IconSize),
                tint = ItmoTheme.colorScheme.primary,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(end = if (trailing) IosMetrics.accessoryGap else 0.dp)
                .padding(vertical = IosMetrics.rowVerticalPadding),
        ) {
            Text(title, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge.wrapping())
            if (!caption.isNullOrEmpty()) {
                Text(caption, color = ItmoTheme.iosColors.secondaryLabel, style = ItmoTheme.typography.bodyMedium)
            }
        }
        if (ownBadge != null) {
            Pill(ownBadge, Modifier.padding(end = ItmoTheme.spacing.content))
        }
        votes?.invoke(this)
    }
}

/** `breakStrategy="high_quality"` with `hyphenationFrequency="normal"`: long words break with a hyphen. */
private fun TextStyle.wrapping(): TextStyle = copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

/** `item_subject_link.xml`'s `android:minHeight`, the 56 dp of a connected group's row. */
private val LinkRowMinHeight = 56.dp

/** The 24 dp category icon of `item_subject_link.xml`. */
private val IconSize = 24.dp

/** The caption's 2 dp `layout_marginTop` in `item_subject_link.xml`. */
private val CaptionGap = 2.dp
