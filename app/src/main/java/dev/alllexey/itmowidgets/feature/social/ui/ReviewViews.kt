package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.ui.shortPersonName
import dev.alllexey.itmowidgets.core.ui.userDisplayName
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemOwnTeacherReviewBinding
import dev.alllexey.itmowidgets.databinding.ItemTeacherReviewBinding
import dev.alllexey.itmowidgets.databinding.ViewReviewVotesBinding
import java.util.Locale

/**
 * Another viewer's or a copied review. The bottom row names the origin — the author, the verification mark or the
 * Reviews source — and holds the votes; reporting sits in the overflow menu. Every mutable property is set here.
 */
internal fun ItemTeacherReviewBinding.bind(item: ProfileItem.Review, actions: ProfileActions) {
    val context = root.context
    val review = item.review
    val meta = listOfNotNull(review.subject, review.written?.text(context)).joinToString(" · ")
    this.meta.text = meta
    this.meta.isVisible = meta.isNotEmpty()
    text.text = review.text
    val community = review.origin as? ReviewOrigin.Community
    val copy = review.origin as? ReviewOrigin.Reviews
    val author = community?.author
    this.author.isVisible = author != null
    // «Фамилия И. О.» keeps the row short; TalkBack still reads the full name.
    this.author.text = author?.let { if (it.name.isBlank()) context.userDisplayName(it.name, it.isu) else shortPersonName(it.name) }
    this.author.contentDescription = author?.let { context.userDisplayName(it.name, it.isu) }
    this.author.setOnClickListener(author?.let { View.OnClickListener { actions.onAuthor(author.isu) } })
    verified.root.isVisible = community?.verified == true
    verified.root.setText(R.string.teacher_review_verified)
    unverified.isVisible = community != null && author == null && !community.verified
    source.isVisible = copy != null
    source.text = copy?.let {
        it.sourceTitle?.let { title -> context.getString(R.string.teacher_review_source, title) }
            ?: context.getString(R.string.teacher_review_source_default)
    }
    source.setOnClickListener(copy?.let { View.OnClickListener { actions.onSource(copy.sourceUrl) } })
    votes.bind(review, item.canVote, item.busy) { up -> actions.onVote(review.id, up) }
    val canReport = item.canReport && community != null && !community.reportedByMe
    // An empty menu keeps its place so the votes line up from card to card.
    more.visibility = if (canReport) View.VISIBLE else View.INVISIBLE
    more.isEnabled = !item.busy
    more.setOnClickListener(if (canReport) View.OnClickListener { anchor ->
        (anchor as ImageButton).showMenu(R.string.teacher_review_report to { actions.onReport(review.id) })
    } else null)
}

/**
 * Arrows only while voting is allowed, the score in the accent once the viewer has voted. Without arrows a zero
 * score says nothing and is left out.
 */
private fun ViewReviewVotesBinding.bind(review: TeacherReview, canVote: Boolean, busy: Boolean, onVote: (up: Boolean) -> Unit) {
    val context = root.context
    voteUp.isVisible = canVote
    voteDown.isVisible = canVote
    score.isVisible = canVote || review.score != 0
    voteUp.isEnabled = !busy
    voteDown.isEnabled = !busy
    score.text = String.format(Locale.getDefault(), "%d", review.score)
    score.contentDescription = context.getString(R.string.teacher_review_score, review.score)
    val accent = context.color.primary
    val neutral = context.color.onSurfaceVariant
    score.setTextColor(if (review.myVote != 0) accent else context.color.onSurface)
    voteUp.imageTintList = ColorStateList.valueOf(if (review.myVote > 0) accent else neutral)
    voteDown.imageTintList = ColorStateList.valueOf(if (review.myVote < 0) accent else neutral)
    voteUp.isSelected = review.myVote > 0
    voteDown.isSelected = review.myVote < 0
    voteUp.setOnClickListener { onVote(true) }
    voteDown.setOnClickListener { onVote(false) }
}

/**
 * The viewer's own review: a status pill while it is not public (or the verification mark once it is), how others
 * see it, a rejection reason, the text and, once published, the read-only score. Editing and deleting are in the menu.
 */
internal fun ItemOwnTeacherReviewBinding.bind(item: ProfileItem.OwnReview, actions: ProfileActions) {
    val context = root.context
    val review = item.review
    bindStatus(context, review)
    val published = review.status == OwnReviewStatus.PUBLISHED
    verified.root.isVisible = published && review.verified
    verified.root.setText(R.string.teacher_review_verified_mine)
    unverified.isVisible = published && !review.verified
    meta.text = listOfNotNull(
        context.getString(R.string.teacher_review_mine),
        review.subject,
        context.getString(if (review.anonymous) R.string.teacher_review_anonymous_mine else R.string.teacher_review_named_mine),
    ).joinToString(" · ")
    val note = review.reviewNote.takeIf { review.status == OwnReviewStatus.REJECTED }
    reason.isVisible = note != null
    reason.text = note?.let { context.getString(R.string.teacher_review_reason, it) }
    text.text = review.text
    score.isVisible = published
    score.text = String.format(Locale.getDefault(), "%d", review.score)
    score.contentDescription = context.getString(R.string.teacher_review_score, review.score)
    more.isEnabled = !item.busy
    more.setOnClickListener { anchor ->
        (anchor as ImageButton).showMenu(
            R.string.teacher_review_edit to actions.onEditReview,
            R.string.teacher_review_delete to actions.onDeleteReview,
        )
    }
}

private fun ItemOwnTeacherReviewBinding.bindStatus(context: Context, review: OwnTeacherReview) {
    val colors = context.color
    val (text, tone) = when (review.status) {
        OwnReviewStatus.PENDING -> R.string.teacher_review_status_pending to colors.tertiary
        OwnReviewStatus.REJECTED -> R.string.teacher_review_status_rejected to colors.error
        OwnReviewStatus.HIDDEN -> R.string.teacher_review_status_hidden to colors.error
        OwnReviewStatus.PUBLISHED -> null to colors.onSurface
    }
    status.isVisible = text != null
    status.text = text?.let(context::getString)
    // A light wash of the tone rather than its container: content-based palettes make containers as dark as the tone.
    status.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(tone, PILL_ALPHA))
    status.setTextColor(tone)
}

private fun ImageButton.showMenu(vararg entries: Pair<Int, () -> Unit>) {
    val popup = PopupMenu(context, this)
    entries.forEachIndexed { index, (title, _) -> popup.menu.add(0, index, index, title) }
    popup.setOnMenuItemClickListener { item ->
        entries.getOrNull(item.itemId)?.second?.invoke()
        true
    }
    popup.show()
}

/** 12 % of the tone, the same wash as `review_verified_container`. */
private const val PILL_ALPHA = 31
