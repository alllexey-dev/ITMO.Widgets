package dev.alllexey.itmowidgets.feature.sport.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLessonKind
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportOccupancy
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_intersection_description
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_error

/*
 * What the `Запись` lesson card and the `Мой спорт` booking card share, in one restrained language
 * (docs/features/sport.md, Cards and details): the outlined card, the title and the time header, fixed-size metadata
 * icons, the kind chip, the friends preview, the small outlined action and the occupancy bar with its tone label.
 * LP-3 and LP-4b reuse these parts.
 */

/** The condition colour of this tone: fixed per theme, never the wallpaper's. */
@Composable
fun SportConditionTone.accent(): Color = with(ItmoTheme.extendedColors) {
    when (this@accent) {
        SportConditionTone.ALLOWED -> sportConditionAllowed
        SportConditionTone.WAITING -> sportConditionWaiting
        SportConditionTone.WARNING -> sportConditionWarning
        SportConditionTone.BLOCKED -> sportConditionBlocked
    }
}

/**
 * The outlined content card of a sport list row (`Widget.ItmoWidgets.Card.Content.Outlined`) inside the list's screen
 * margin; the whole card opens the details, or nothing when [onClick] is null (another user's read-only list).
 * [bottomPadding] lets a card whose last row ends in a [SportCardAction] align the action's visible outline, not its
 * touch target, with the card padding ([sportCardBottomPadding]).
 */
@Composable
fun SportCardSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = ItmoTheme.spacing.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The margin goes outside [modifier], so a test tag or a size on it describes the card itself.
    val placed = Modifier
        .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.related)
        .then(modifier)
        .fillMaxWidth()
    val shape = ItmoTheme.shapes.cardContent
    val color = ItmoTheme.colorScheme.surfaceContainerLow
    val border = BorderStroke(ItmoTheme.shapes.cardStroke, ItmoTheme.colorScheme.outlineVariant)
    val body: @Composable () -> Unit = {
        Column(
            Modifier.padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.cardPadding,
                end = ItmoTheme.spacing.cardPadding,
                bottom = bottomPadding,
            ),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = placed, shape = shape, color = color, border = border, content = body)
    } else {
        Surface(modifier = placed, shape = shape, color = color, border = border, content = body)
    }
}

/**
 * The card's bottom padding: the card padding, less the part of the touch target that [SportCardAction] keeps under
 * its visible outline when the action is the card's last row, so the outline's bottom gap equals its end gap.
 */
@Composable
fun sportCardBottomPadding(endsWithAction: Boolean): Dp {
    val padding = ItmoTheme.spacing.cardPadding
    if (!endsWithAction) return padding
    val inset = ((ItmoTheme.spacing.touchTarget - ActionHeight) / 2).coerceAtLeast(0.dp)
    return padding - inset
}

/** The section title: up to two balanced lines. */
@Composable
fun SportCardTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier.fillMaxWidth(),
        color = ItmoTheme.colorScheme.onSurface,
        style = ItmoTheme.typography.titleMedium.copy(lineBreak = LineBreak.Heading),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The time line under the title: the start time is the scanning anchor and the only metadata on `onSurface`, so it
 * is measured first and never shortened; the kind chip takes the rest of the row and gives way first.
 */
@Composable
fun SportCardTimeRow(
    time: String,
    kind: SportLessonKind,
    modifier: Modifier = Modifier,
    timeModifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            time,
            timeModifier,
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleSmall,
            maxLines = 1,
        )
        Box(
            Modifier
                .weight(1f)
                .padding(start = ItmoTheme.spacing.compact),
            contentAlignment = Alignment.CenterEnd,
        ) {
            SportKindChip(kind, Modifier.testTag(SportCardTestTags.KIND))
        }
        trailing()
    }
}

/** The lesson kind as a filled chip on `surfaceContainerHighest`: the short name shown, the full one read aloud. */
@Composable
fun SportKindChip(kind: SportLessonKind, modifier: Modifier = Modifier) {
    val description = stringResource(kind.titleResource())
    Text(
        stringResource(kind.compactTitleResource()),
        modifier
            .semantics { contentDescription = description }
            .background(ItmoTheme.colorScheme.surfaceContainerHighest, ItmoTheme.shapes.small)
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = ChipVerticalPadding),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The warning mark of a lesson that intersects the user's schedule. */
@Composable
fun SportIntersectionMark(modifier: Modifier = Modifier) {
    Icon(
        painterResource(KitRes.drawable.ic_error),
        contentDescription = stringResource(Res.string.sport_intersection_description),
        modifier = modifier
            .padding(start = ItmoTheme.spacing.compact)
            .size(MetaIconSize),
        tint = SportConditionTone.WARNING.accent(),
    )
}

/** A metadata line (teacher, place) behind a fixed-size decorative icon; one line, shortened at the end. */
@Composable
fun SportMetaRow(
    icon: DrawableResource,
    text: String,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(MetaIconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(ItmoTheme.spacing.compact))
        Text(
            text,
            textModifier.weight(1f),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Up to three overlapping friend avatars and their count; nothing without friends. */
@Composable
fun SportFriendsPreview(friends: List<FriendSportBooking>, modifier: Modifier = Modifier) {
    if (friends.isEmpty()) return
    val shown = friends.take(MAX_FRIEND_AVATARS)
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(FriendAvatarSize + FriendAvatarStep * (shown.size - 1))
                .height(FriendAvatarSize)
                .clearAndSetSemantics {},
        ) {
            shown.forEachIndexed { index, booking ->
                Avatar(
                    booking.friend.name,
                    booking.friend.pictureUrl,
                    Modifier.padding(start = FriendAvatarStep * index),
                    size = FriendAvatarSize,
                )
            }
        }
        Text(
            sportFriendsCountText(friends.size).asString(),
            Modifier
                .weight(1f)
                .padding(start = ItmoTheme.spacing.compact),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodySmall,
        )
    }
}

/**
 * The card's small outlined action in [tone] (neutral `onSurfaceVariant` without one): the kit's [ProgressButton] with
 * the outline and the label in the tone's colour, drawn [ActionHeight] high inside its 48 dp touch target. While
 * [busy] it shows its progress and is disabled for accessibility, so a request in flight cannot be sent twice.
 */
@Composable
fun SportCardAction(
    label: String,
    tone: SportConditionTone?,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = tone?.accent() ?: ItmoTheme.colorScheme.onSurfaceVariant
    val scheme = MaterialTheme.colorScheme
    // Material's outlined button takes its label from onSurfaceVariant and its outline from outline(Variant).
    val toned = scheme.copy(primary = accent, onSurfaceVariant = accent, outline = accent, outlineVariant = accent)
    MaterialTheme(colorScheme = toned) {
        ProgressButton(
            label = label,
            onClick = onClick,
            // A minimum height replaces Material's 40 dp default; the button still reserves its 48 dp touch target.
            // While busy the button keeps its tone, as the View did, and reads as disabled; the kit ignores the tap.
            modifier = modifier
                .heightIn(min = ActionHeight)
                .semantics { if (busy) disabled() },
            style = ProgressButtonStyle.Outlined,
            inProgress = busy,
        )
    }
}

/**
 * The full-width occupancy bar of a real lesson: occupied places over the limit in the [SportOccupancy.tone]'s
 * colour on an `outlineVariant` track. Decorative: [SportOccupancyLabel] says the same in words.
 */
@Composable
fun SportOccupancyBar(occupancy: SportOccupancy, modifier: Modifier = Modifier) {
    val fraction = occupancy.occupied.toFloat() / occupancy.limit
    Box(
        modifier
            .fillMaxWidth()
            .height(OccupancyTrackHeight)
            .clip(ItmoTheme.shapes.full)
            .background(ItmoTheme.colorScheme.outlineVariant)
            .clearAndSetSemantics {},
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(OccupancyTrackHeight)
                    .clip(ItmoTheme.shapes.full)
                    .background(occupancy.tone().accent()),
            )
        }
    }
}

/** `Занято 13/20`: only a scarce or full lesson is worth an accent; a roomy one stays quiet. */
@Composable
fun SportOccupancyLabel(occupancy: SportOccupancy, modifier: Modifier = Modifier) {
    val tone = occupancy.tone()
    Text(
        occupancy.cardText().asString(),
        modifier,
        color = if (tone == SportConditionTone.WAITING) ItmoTheme.colorScheme.onSurfaceVariant else tone.accent(),
        style = ItmoTheme.typography.labelMedium,
    )
}

/** Tags of the shared parts for host tests. */
object SportCardTestTags {
    /** The kind chip, which gives way first when the time row is tight. */
    const val KIND = "sport_card_kind"
}

/** `item_sport_lesson.xml`'s button: 48 dp high with 6 dp insets above and below the outline. */
private val ActionHeight = 36.dp

/** `item_sport_lesson.xml`'s chip: 3 dp above and below the label, off the 4 dp grid. */
private val ChipVerticalPadding = 3.dp

/** Metadata and the intersection mark keep one icon size at every font scale. */
private val MetaIconSize = 16.dp

/** `item_sport_friends.xml`: 28 dp avatars, each 20 dp after the previous one. */
private val FriendAvatarSize = 28.dp
private val FriendAvatarStep = 20.dp
private const val MAX_FRIEND_AVATARS = 3

/** `trackThickness` 6 dp with 3 dp corners: a fully rounded bar. */
private val OccupancyTrackHeight = 6.dp
