package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.designsystem.components.buttons.Pill
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.components.rows.VotePill
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_anonymous_mine
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_delete
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_edit
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_mine_badge
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_named_mine
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_reason
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_score
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_verified_mine
import org.jetbrains.compose.resources.stringResource

/**
 * The viewer's own review, a group of its own above the others (port of `ReviewViews.kt` and
 * `item_own_teacher_review.xml`): `мой`, a status pill while it is not public (`На проверке` in tertiary, `Отклонён`
 * or `Скрыт` in error), the more button with `Изменить` and `Удалить`, the subject and how others see it, a rejection reason, the
 * full text and, once published, the verification and the read-only score. While [busy] (a deletion in flight) the more button
 * ignores taps.
 */
@Composable
fun OwnTeacherReviewRow(
    review: OwnTeacherReview,
    busy: Boolean,
    actions: TeacherReviewActions,
    modifier: Modifier = Modifier,
) {
    val published = review.status == OwnReviewStatus.PUBLISHED
    val note = review.reviewNote.takeIf { review.status == OwnReviewStatus.REJECTED }
    val visibility = stringResource(
        if (review.anonymous) Res.string.teacher_review_anonymous_mine else Res.string.teacher_review_named_mine,
    )
    val menu = listOf(
        ItmoMenuItem(stringResource(Res.string.teacher_review_edit), actions.onEdit),
        ItmoMenuItem(stringResource(Res.string.teacher_review_delete), actions.onDelete),
    )
    val textEnd = Modifier.fillMaxWidth().padding(end = ItmoTheme.spacing.content)
    Column(
        modifier
            .fillMaxWidth()
            .connectedGroupItem(GroupPosition.Single)
            .testTag(TeacherReviewTestTags.OWN_REVIEW)
            .reviewPadding(top = true, footer = published),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = ItmoTheme.spacing.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            Pill(stringResource(Res.string.teacher_review_mine_badge))
            review.status.label()?.let { status ->
                Pill(
                    stringResource(status),
                    Modifier.padding(start = ItmoTheme.spacing.compact),
                    tone = review.status.tone(),
                )
            }
            Spacer(Modifier.weight(1f))
            ReviewMoreButton(menu, enabled = !busy, modifier = Modifier.testTag(TeacherReviewTestTags.MORE))
        }
        Text(
            listOfNotNull(review.subject, visibility).joinToString(", "),
            textEnd,
            style = ItmoTheme.typography.bodySmall,
            color = ItmoTheme.colorScheme.onSurfaceVariant,
        )
        if (note != null) {
            Text(
                stringResource(Res.string.teacher_review_reason, note),
                textEnd.padding(top = ItmoTheme.spacing.related),
                style = ItmoTheme.typography.bodySmall,
                color = ItmoTheme.colorScheme.error,
            )
        }
        Text(
            review.text,
            textEnd.padding(top = ItmoTheme.spacing.related),
            style = ItmoTheme.typography.bodyMedium,
            color = ItmoTheme.colorScheme.onSurface,
        )
        if (published) {
            Row(
                Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.compact),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = ItmoTheme.spacing.compact)) {
                    if (review.verified) VerifiedLine(Res.string.teacher_review_verified_mine) else UnverifiedLine()
                }
                VotePill(
                    score = review.score,
                    myVote = null,
                    scoreDescription = stringResource(Res.string.teacher_review_score, review.score),
                    onVote = null,
                    labels = reviewVoteLabels(),
                )
            }
        }
    }
}

@Composable
private fun OwnReviewStatus.tone() = when (this) {
    OwnReviewStatus.PENDING -> ItmoTheme.colorScheme.tertiary
    OwnReviewStatus.REJECTED, OwnReviewStatus.HIDDEN -> ItmoTheme.colorScheme.error
    OwnReviewStatus.PUBLISHED -> ItmoTheme.colorScheme.onSurface
}
