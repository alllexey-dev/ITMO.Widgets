package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenu
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.components.rows.Vote
import dev.alllexey.itmowidgets.designsystem.components.rows.VoteLabels
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_more_vert
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_actions
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_status_hidden
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_status_pending
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_status_rejected
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_unverified
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_vote_down
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_vote_up
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * What the review rows report, in `UserProfileViewModel`'s terms. [onVote] names the arrow that was tapped (`up`);
 * tapping the arrow of the current vote takes it back upstream. [onEdit] and [onDelete] belong to the viewer's own
 * review.
 */
class TeacherReviewActions(
    val onVote: (reviewId: String, up: Boolean) -> Unit = { _, _ -> },
    val onReport: (reviewId: String) -> Unit = {},
    val onAuthor: (isu: Int) -> Unit = {},
    val onSource: (url: String) -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/** Tags a test or a host finds in the review rows and the summary card. */
object TeacherReviewTestTags {
    const val REVIEW = "teacher_review"
    const val OWN_REVIEW = "teacher_review_own"
    const val MORE = "teacher_review_more"
    const val AUTHOR = "teacher_review_author"
    const val SOURCE = "teacher_review_source"
    const val SUMMARY = "teacher_summary"
    const val SUMMARY_TOGGLE = "teacher_summary_toggle"
    const val SUMMARY_SCALES = "teacher_summary_scales"
}

/** Backend's -1, 0 or 1 as the pill's vote. */
internal fun voteOf(myVote: Int): Vote? = when {
    myVote > 0 -> Vote.Up
    myVote < 0 -> Vote.Down
    else -> null
}

@Composable
internal fun reviewVoteLabels(): VoteLabels =
    VoteLabels(stringResource(Res.string.teacher_review_vote_up), stringResource(Res.string.teacher_review_vote_down))

/**
 * One edge for every review row: a 48 dp top line (caption or badges with the more button) needs only 4 dp above it, a row without
 * one gets the 16 dp content padding; a footer ends in the 48 dp row of the votes, so 12 dp keep it clear of the edge,
 * and a row that ends with its text (or a rejection reason) gets 16 dp. The end keeps 4 dp for the more button's target; texts add
 * 12 dp to reach the 16 dp content edge.
 */
@Composable
internal fun Modifier.reviewPadding(top: Boolean, footer: Boolean): Modifier {
    val spacing = ItmoTheme.spacing
    return padding(
        start = spacing.cardPadding,
        top = if (top) spacing.related else spacing.cardPadding,
        end = spacing.related,
        bottom = if (footer) spacing.content else spacing.cardPadding,
    )
}

/**
 * the more button with its menu: a 48 dp target. A row without actions keeps it in place, invisible and silent, so captions break
 * at one width from row to row.
 */
@Composable
internal fun ReviewMoreButton(items: List<ItmoMenuItem>, enabled: Boolean, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val visible = items.isNotEmpty()
    val description = stringResource(Res.string.teacher_review_actions)
    val target = if (visible) {
        Modifier
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = null,
                indication = ripple(bounded = false, radius = MoreRippleRadius),
                onClick = { expanded = true },
            )
            .semantics { contentDescription = description }
    } else {
        Modifier.alpha(0f).clearAndSetSemantics {}
    }
    Box(modifier.size(ItmoTheme.spacing.touchTarget).then(target), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(KitRes.drawable.ic_more_vert),
            contentDescription = null,
            modifier = Modifier.size(MoreIconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
        if (visible) ItmoMenu(expanded, onDismissRequest = { expanded = false }, groups = listOf(items))
    }
}

/** `Вёл у автора` or `Вёл у вас` with a 16 dp check, both in the accent. */
@Composable
internal fun VerifiedLine(text: StringResource, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(KitRes.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.padding(end = ItmoTheme.spacing.related).size(VerifiedIconSize),
            tint = ItmoTheme.colorScheme.primary,
        )
        Text(stringResource(text), style = ItmoTheme.typography.bodySmall, color = ItmoTheme.colorScheme.primary)
    }
}

@Composable
internal fun UnverifiedLine(modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.teacher_review_unverified),
        modifier,
        style = ItmoTheme.typography.bodySmall,
        color = ItmoTheme.colorScheme.onSurfaceVariant,
    )
}

/** The status pill's words; a published review shows none. */
internal fun OwnReviewStatus.label(): StringResource? = when (this) {
    OwnReviewStatus.PENDING -> Res.string.teacher_review_status_pending
    OwnReviewStatus.REJECTED -> Res.string.teacher_review_status_rejected
    OwnReviewStatus.HIDDEN -> Res.string.teacher_review_status_hidden
    OwnReviewStatus.PUBLISHED -> null
}

/**
 * The View's negative margins: the content keeps its measured size and target, but reports [top], [bottom] and
 * [start] less to the parent and is drawn shifted by them, so a 48 dp target reaches into the space around it.
 */
internal fun Modifier.overlap(top: Dp = 0.dp, bottom: Dp = 0.dp, start: Dp = 0.dp): Modifier = layout { measurable, constraints ->
    val vertical = (top + bottom).roundToPx()
    val horizontal = start.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = 0,
            minWidth = 0,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + vertical else constraints.maxHeight,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + horizontal else constraints.maxWidth,
        ),
    )
    val width = (placeable.width - horizontal).coerceAtLeast(0)
    val height = (placeable.height - vertical).coerceAtLeast(0)
    layout(width, height) { placeable.place(-horizontal, -top.roundToPx()) }
}

/** The 24 dp `ic_more_vert` in its 48 dp target (12 dp padding). */
private val MoreIconSize = 24.dp

/** `selectableItemBackgroundBorderless` over a 48 dp target. */
private val MoreRippleRadius = 24.dp

/** The verification check. */
private val VerifiedIconSize = 16.dp
