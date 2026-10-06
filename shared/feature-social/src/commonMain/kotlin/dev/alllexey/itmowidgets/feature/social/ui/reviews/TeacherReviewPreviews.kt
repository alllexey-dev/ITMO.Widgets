package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.ui.reviews.ReviewPreviewFixtures as F

/** A named verified review the viewer voted up, an anonymous unverified one below zero, one already reported. */
@Preview(name = "community")
@Composable
private fun TeacherReviewRowPreview() = ItmoPreview {
    ReviewGroup(listOf(F.named, F.anonymous, F.reported), canVote = true, canReport = true)
}

/** Copies from Reviews with and without a source title, a date or a caption, without the right to vote. */
@Preview(name = "copies")
@Composable
private fun TeacherReviewRowCopiesPreview() = ItmoPreview {
    ReviewGroup(listOf(F.copy, F.oldCopy, F.bareCopy), canVote = false, canReport = true)
}

/** A long caption, text and author; an author without a published name; a vote in flight on the second row. */
@Preview(name = "long")
@Composable
private fun TeacherReviewRowLongPreview() = ItmoPreview {
    ReviewGroup(listOf(F.long, F.unnamedAuthor), canVote = true, canReport = false, busyId = F.unnamedAuthor.id)
}

/** The own review on its way: on moderation, rejected with a reason, hidden. */
@Preview(name = "moderation")
@Composable
private fun OwnTeacherReviewRowPreview() = ItmoPreview {
    PreviewColumn {
        OwnTeacherReviewRow(F.own, busy = false, actions = TeacherReviewActions())
        OwnTeacherReviewRow(F.ownRejected, busy = false, actions = TeacherReviewActions())
        OwnTeacherReviewRow(F.ownHidden, busy = false, actions = TeacherReviewActions())
    }
}

/** The own review published: verified with its name, and unverified, anonymous, below zero. */
@Preview(name = "published")
@Composable
private fun OwnTeacherReviewRowPublishedPreview() = ItmoPreview {
    PreviewColumn {
        OwnTeacherReviewRow(F.ownPublished, busy = false, actions = TeacherReviewActions())
        OwnTeacherReviewRow(F.ownPublishedUnverified, busy = true, actions = TeacherReviewActions())
    }
}

@Composable
private fun ReviewGroup(reviews: List<TeacherReview>, canVote: Boolean, canReport: Boolean, busyId: String? = null) {
    PreviewColumn(spaced = false) {
        reviews.forEachIndexed { index, review ->
            TeacherReviewRow(
                review,
                GroupPosition.of(index, reviews.size),
                canVote = canVote,
                canReport = canReport,
                busy = review.id == busyId,
                actions = TeacherReviewActions(),
            )
        }
    }
}

@Composable
internal fun PreviewColumn(spaced: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = if (spaced) Arrangement.spacedBy(ItmoTheme.spacing.group) else Arrangement.Top,
        content = content,
    )
}
