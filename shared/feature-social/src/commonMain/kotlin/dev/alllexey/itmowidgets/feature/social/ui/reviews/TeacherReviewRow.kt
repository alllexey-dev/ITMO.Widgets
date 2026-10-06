package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.shortPersonName
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.components.rows.Vote
import dev.alllexey.itmowidgets.designsystem.components.rows.VotePill
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.displayName
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_anonymous
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_report
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_score
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_source
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_source_default
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_verified
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * Another viewer's or a copied review as a row of the others' connected group (port of `ReviewViews.kt` and
 * `item_teacher_review.xml`). The top line is the caption (subject, date) in at most two lines with the more button for reporting;
 * the text is never truncated; the footer names who wrote it (a named author or the source, both links, otherwise
 * `Анонимный отзыв`) with the verification under it and the vote pill centred beside them.
 *
 * [canVote] shows the arrows; without them a zero score is left out. [canReport] is the section's right; the more button offers
 * `Пожаловаться` only on a community review the viewer has not reported yet and otherwise keeps its place invisibly.
 * While [busy] (a vote in flight) the arrows and the more button ignore taps.
 */
@Composable
fun TeacherReviewRow(
    review: TeacherReview,
    position: GroupPosition,
    canVote: Boolean,
    canReport: Boolean,
    busy: Boolean,
    actions: TeacherReviewActions,
    modifier: Modifier = Modifier,
) {
    val community = review.origin as? ReviewOrigin.Community
    val copy = review.origin as? ReviewOrigin.Reviews
    val author = community?.author
    val reportable = canReport && community != null && !community.reportedByMe
    val meta = listOfNotNull(review.subject, review.written?.text()).joinToString(", ")
    val showsOrigin = community != null || copy != null
    val showsVotes = canVote || review.score != 0
    val top = meta.isNotEmpty() || reportable
    val footer = showsOrigin || showsVotes
    val reportLabel = stringResource(Res.string.teacher_review_report)
    Column(
        modifier
            .fillMaxWidth()
            .connectedGroupItem(position)
            .testTag(TeacherReviewTestTags.REVIEW)
            .reviewPadding(top, footer),
    ) {
        if (top) {
            Row(Modifier.fillMaxWidth().heightIn(min = ItmoTheme.spacing.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meta,
                    Modifier.weight(1f).padding(end = ItmoTheme.spacing.compact),
                    style = ItmoTheme.typography.bodySmall,
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ReviewMoreButton(
                    items = if (reportable) listOf(ItmoMenuItem(reportLabel, { actions.onReport(review.id) })) else emptyList(),
                    enabled = !busy,
                    modifier = Modifier.testTag(TeacherReviewTestTags.MORE),
                )
            }
        }
        Text(
            review.text,
            Modifier.fillMaxWidth().padding(end = ItmoTheme.spacing.content),
            style = ItmoTheme.typography.bodyMedium,
            color = ItmoTheme.colorScheme.onSurface,
        )
        if (footer) {
            Row(
                Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.compact),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = ItmoTheme.spacing.compact)) {
                    // The links keep a 48 dp target; the line below tucks into its padding.
                    when {
                        author != null -> OriginLink(
                            text = author.shortName(),
                            description = author.displayName().asString(),
                            color = ItmoTheme.colorScheme.primary,
                            onClick = { actions.onAuthor(author.isu) },
                            modifier = Modifier.testTag(TeacherReviewTestTags.AUTHOR),
                        )
                        copy != null -> OriginLink(
                            // The label is just the source; `Reviews` stays for TalkBack and for a copy without a title.
                            text = copy.sourceTitle ?: stringResource(Res.string.teacher_review_source_default),
                            description = copy.sourceTitle?.let { stringResource(Res.string.teacher_review_source, it) },
                            color = ItmoTheme.colorScheme.onSurfaceVariant,
                            onClick = { actions.onSource(copy.sourceUrl) },
                            modifier = Modifier.testTag(TeacherReviewTestTags.SOURCE),
                            external = true,
                        )
                        community != null -> Text(
                            stringResource(Res.string.teacher_review_anonymous),
                            Modifier
                                .overlap(top = OriginOverlap, bottom = OriginOverlap)
                                .heightIn(min = ItmoTheme.spacing.touchTarget)
                                .padding(vertical = ItmoTheme.spacing.content),
                            style = ItmoTheme.typography.bodyMedium,
                            color = ItmoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        community?.verified == true -> VerifiedLine(
                            Res.string.teacher_review_verified,
                            Modifier.padding(top = ItmoTheme.spacing.related),
                        )
                        community != null -> UnverifiedLine(Modifier.padding(top = ItmoTheme.spacing.related))
                    }
                }
                if (showsVotes) {
                    VotePill(
                        score = review.score,
                        myVote = voteOf(review.myVote),
                        scoreDescription = stringResource(Res.string.teacher_review_score, review.score),
                        onVote = if (canVote) { vote -> actions.onVote(review.id, vote == Vote.Up) } else null,
                        labels = reviewVoteLabels(),
                        enabled = !busy,
                    )
                }
            }
        }
    }
}

/**
 * Who wrote a review as a text link: a 48 dp target whose 12 dp padding above and below reaches into the space
 * around it, as `Widget.ItmoWidgets.ReviewOriginButton` with its -12 dp margins did. A source link ends in a 16 dp
 * `ic_open_in_new`.
 */
@Composable
private fun OriginLink(
    text: String,
    description: String?,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    external: Boolean = false,
) {
    // The overlap sits on a wrapper, so the link's own node keeps its full 48 dp.
    Box(modifier.overlap(top = OriginOverlap, bottom = OriginOverlap)) {
        Row(
            Modifier
                .sizeIn(minWidth = ItmoTheme.spacing.touchTarget, minHeight = ItmoTheme.spacing.touchTarget)
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description ?: text
                    role = Role.Button
                }
                .padding(end = ItmoTheme.spacing.compact, top = ItmoTheme.spacing.content, bottom = ItmoTheme.spacing.content),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, Modifier.weight(1f, fill = false), style = ItmoTheme.typography.bodyMedium, color = color)
            if (external) {
                Icon(
                    painterResource(KitRes.drawable.ic_open_in_new),
                    contentDescription = null,
                    modifier = Modifier.padding(start = ItmoTheme.spacing.related).size(SourceIconSize),
                    tint = ItmoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** `Фамилия И. О.` keeps the row short; TalkBack still reads the full name. */
@Composable
private fun UserSummary.shortName(): String =
    if (name.isBlank()) displayName().asString() else shortPersonName(name)

/** The -12 dp margins around the origin: as deep as its link's vertical padding. */
private val OriginOverlap = 12.dp

/** `iconSize` of the source link. */
private val SourceIconSize = 16.dp
