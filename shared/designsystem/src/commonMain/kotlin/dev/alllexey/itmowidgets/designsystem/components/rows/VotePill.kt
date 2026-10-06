package dev.alllexey.itmowidgets.designsystem.components.rows

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_drop_down
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_drop_up
import org.jetbrains.compose.resources.painterResource

/** A vote on a rated item: a link or a teacher review. */
enum class Vote {
    Up,
    Down,
}

/** What the arrows of a [VotePill] say to TalkBack and offer as its custom actions. */
class VoteLabels(
    /** The up arrow, such as `Полезная ссылка`. */
    val up: String,
    /** The down arrow, such as `Бесполезная ссылка`. */
    val down: String,
)

/**
 * The compact vote pill (up arrow, score, down arrow) of others' links and reviews (port of
 * `view_link_vote_pill.xml` and `core/ui/SubjectLinkRow.kt`'s `bindVotes`): a 32 dp pill inside the 48 dp row of the
 * arrow targets. The score turns `primary` once the viewer voted ([myVote]) and `error` below zero; the arrow of the
 * own vote is `primary`.
 *
 * Without [onVote] the pill shows the score alone. Tapping the arrow of the current vote takes it back upstream, so
 * [onVote] only reports which arrow was tapped. A disabled pill (a vote in flight) keeps its look and ignores taps.
 * The score reads [scoreDescription] and carries both votes as TalkBack custom actions, so a row that merges the pill
 * offers them too.
 *
 * Under the iOS style the arrows are 44 pt targets that dim while pressed instead of a ripple and the pill takes
 * `tertiarySystemFill`; the pill stays 32 high with the same room around the score and the arrows.
 */
@Composable
fun VotePill(
    score: Int,
    myVote: Vote?,
    scoreDescription: String,
    onVote: ((Vote) -> Unit)?,
    labels: VoteLabels,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ios = ItmoTheme.platformStyle == ItmoPlatformStyle.Ios
    val container = if (ios) ItmoTheme.iosColors.tertiarySystemFill else ItmoTheme.colorScheme.surfaceContainerHighest
    // The pill keeps its 32 dp inside the arrows' targets of either style: 16 x 8 dp in Material's 48, 12 x 6 in 44.
    val insetHorizontal = if (ios) ItmoTheme.spacing.touchTarget - PillHeight else PillInsetHorizontal
    val insetVertical = if (ios) (ItmoTheme.spacing.touchTarget - PillHeight) / 2 else PillInsetVertical
    Row(
        modifier
            .height(ItmoTheme.spacing.touchTarget)
            .drawBehind {
                val inset = Offset(insetHorizontal.toPx(), insetVertical.toPx())
                drawRoundRect(
                    color = container,
                    topLeft = inset,
                    size = Size(size.width - 2 * inset.x, size.height - 2 * inset.y),
                    cornerRadius = CornerRadius(PillRadius.toPx()),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onVote != null) VoteArrow(Vote.Up, myVote, labels.up, enabled, onVote)
        Score(
            score,
            myVote,
            scoreDescription,
            labels,
            margin = if (onVote != null) 0.dp else insetHorizontal + ItmoTheme.spacing.content,
            actions = onVote.takeIf { enabled },
        )
        if (onVote != null) VoteArrow(Vote.Down, myVote, labels.down, enabled, onVote)
    }
}

@Composable
private fun Score(
    score: Int,
    myVote: Vote?,
    description: String,
    labels: VoteLabels,
    margin: Dp,
    actions: ((Vote) -> Unit)?,
) {
    val color = when {
        myVote != null -> ItmoTheme.colorScheme.primary
        score < 0 -> ItmoTheme.colorScheme.error
        else -> ItmoTheme.colorScheme.onSurface
    }
    // Alone, the number keeps the pill's inset plus 12 dp; the arrows pad their icons towards it instead.
    Text(
        formatScore(score),
        Modifier
            .padding(horizontal = margin)
            .widthIn(min = ScoreMinWidth)
            .semantics {
                contentDescription = description
                if (actions != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(labels.up) { actions(Vote.Up); true },
                        CustomAccessibilityAction(labels.down) { actions(Vote.Down); true },
                    )
                }
            },
        color = color,
        style = ItmoTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun VoteArrow(vote: Vote, myVote: Vote?, label: String, enabled: Boolean, onVote: (Vote) -> Unit) {
    val selected = myVote == vote
    val icon = painterResource(if (vote == Vote.Up) Res.drawable.ic_arrow_drop_up else Res.drawable.ic_arrow_drop_down)
    val target = if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Modifier
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = { onVote(vote) },
            )
            .graphicsLayer { alpha = if (pressed) IOS_PRESSED_ALPHA else 1f }
    } else {
        Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.Button,
            interactionSource = null,
            indication = ripple(bounded = false, radius = ArrowRippleRadius),
            onClick = { onVote(vote) },
        )
    }
    Box(
        Modifier
            .size(ItmoTheme.spacing.touchTarget)
            .then(target)
            .semantics { contentDescription = label },
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .align(if (vote == Vote.Up) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(
                    start = if (vote == Vote.Up) 0.dp else ArrowInnerPadding,
                    end = if (vote == Vote.Up) ArrowInnerPadding else 0.dp,
                )
                .size(ArrowIconSize),
            tint = if (selected) ItmoTheme.colorScheme.primary else ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The score with a true minus sign below zero, as the View pill wrote it. */
internal fun formatScore(score: Int): String = if (score < 0) "$MINUS_SIGN${-score}" else score.toString()

private const val MINUS_SIGN = '\u2212'

/** `bg_link_vote_pill`: the pill is inset 16 dp from the arrows' outer edges and 8 dp from the 48 dp row. */
private val PillInsetHorizontal = 16.dp
private val PillInsetVertical = 8.dp

/** `bg_link_vote_pill`'s height: 48 dp less twice [PillInsetVertical]. */
private val PillHeight = 32.dp

/** How much a pressed arrow dims under the iOS style, as UIKit's plain buttons fade instead of a ripple. */
private const val IOS_PRESSED_ALPHA = 0.5f

/** `bg_link_vote_pill`'s 16 dp corners: a fully round 32 dp pill. */
private val PillRadius = 16.dp

/** The arrows' 4 dp padding towards the score in `view_link_vote_pill.xml`. */
private val ArrowInnerPadding = 4.dp

/** The 24 dp `ic_arrow_drop_up` and `ic_arrow_drop_down` inside 48 dp targets. */
private val ArrowIconSize = 24.dp

/** `selectableItemBackgroundBorderless` over a 48 dp target. */
private val ArrowRippleRadius = 24.dp

/** The score's `android:minWidth`. */
private val ScoreMinWidth = 12.dp
